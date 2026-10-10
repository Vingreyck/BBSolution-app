@file:OptIn(ExperimentalForeignApi::class)

package br.com.selebi.app.nucleo.rastreio

import br.com.selebi.app.nucleo.Nucleo
import br.com.selebi.app.nucleo.banco.PosicaoEntidade
import br.com.selebi.app.nucleo.dados.Ponto
import br.com.selebi.app.nucleo.dados.ponto
import br.com.selebi.app.nucleo.modelo.Campo
import br.com.selebi.app.nucleo.modelo.ModoRastreio
import br.com.selebi.app.nucleo.util.Datas
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import platform.CoreLocation.CLActivityTypeAutomotiveNavigation
import platform.CoreLocation.CLActivityTypeOther
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.CoreLocation.kCLLocationAccuracyHundredMeters
import platform.UIKit.UIDevice
import platform.darwin.NSObject
import kotlin.math.roundToInt

/**
 * O GPS do técnico em campo no iPhone, com o app aberto, minimizado ou com a
 * tela apagada no bolso.
 *
 * Ligado com o app na frente (no "Estou a caminho"), o iOS deixa continuar em
 * segundo plano com a permissão "durante o uso" — e mostra a faixa azul de
 * localização, o equivalente da notificação fixa do Android: o técnico sempre
 * sabe que está sendo rastreado.
 *
 * O iPhone não manda posição por tempo, só por movimento. Parado no local (ou
 * no semáforo), a última posição é repetida no intervalo do modo — como o
 * Android faz — para o escritório não achar que o sinal caiu.
 */
class MotorRastreioIos(private val memoria: MemoriaRastreio) : MotorRastreio {
    private var nucleo: Nucleo? = null

    // O CoreLocation só funciona na thread principal.
    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var gerente: CLLocationManager? = null
    private val delegado = DelegadoRastreio { aoLocalizar(it) }
    private var estado: EstadoRastreio? = null
    private var ultimoGuardadoMs: Long? = null
    private var laco: Job? = null

    /** O núcleo é montado depois do motor (ele é uma das peças): liga os dois aqui. */
    fun conectar(nucleo: Nucleo) {
        this.nucleo = nucleo
    }

    override fun ligar(estado: EstadoRastreio, primeiro: Ponto?) {
        escopo.launch {
            val g = gerente ?: CLLocationManager().also {
                it.delegate = delegado
                gerente = it
            }
            UIDevice.currentDevice.batteryMonitoringEnabled = true
            val mudou = estado != this@MotorRastreioIos.estado
            this@MotorRastreioIos.estado = estado
            when (estado.modo) {
                ModoRastreio.DESLOCAMENTO -> {
                    g.desiredAccuracy = kCLLocationAccuracyBest
                    g.distanceFilter = 10.0
                    g.activityType = CLActivityTypeAutomotiveNavigation
                }
                ModoRastreio.ECO -> {
                    g.desiredAccuracy = kCLLocationAccuracyHundredMeters
                    g.distanceFilter = 25.0
                    g.activityType = CLActivityTypeOther
                }
            }
            g.pausesLocationUpdatesAutomatically = false
            g.allowsBackgroundLocationUpdates = true
            g.showsBackgroundLocationIndicator = true
            g.startUpdatingLocation()
            if (primeiro != null) {
                guardar(estado, primeiro.latitude, primeiro.longitude, primeiro.precisao?.toFloat(), null, forcar = true)
            }
            if (mudou || laco?.isActive != true) reiniciarEnvio(estado)
        }
    }

    override fun desligar() {
        escopo.launch {
            gerente?.stopUpdatingLocation()
            gerente?.allowsBackgroundLocationUpdates = false
            laco?.cancel()
            estado = null
            ultimoGuardadoMs = null
            // O que não subiu fica no banco, e o envio da fila leva quando der.
            nucleo?.agendador?.agendar()
        }
    }

    private fun aoLocalizar(local: CLLocation) {
        val atual = estado ?: return
        // Desligado por fora (a OS saiu de campo, a pessoa saiu do app): para aqui também.
        if (memoria.ler()?.ordemId != atual.ordemId) {
            desligar()
            return
        }
        val p = ponto(local)
        guardar(
            atual,
            p.latitude,
            p.longitude,
            p.precisao?.toFloat(),
            local.speed.takeIf { it >= 0 }?.toFloat(),
            forcar = false,
        )
    }

    private fun guardar(
        atual: EstadoRastreio,
        latitude: Double,
        longitude: Double,
        precisao: Float?,
        velocidade: Float?,
        forcar: Boolean,
    ) {
        val agora = Datas.agoraMs()
        if (!forcar && !Campo.deveGuardar(ultimoGuardadoMs, agora, precisao, atual.modo)) return
        ultimoGuardadoMs = agora
        val n = nucleo ?: return
        val posicao = PosicaoEntidade(
            usuarioId = atual.usuarioId,
            ordemId = atual.ordemId,
            latitude = latitude,
            longitude = longitude,
            precisao = precisao,
            velocidade = velocidade,
            bateria = bateria(),
            modo = atual.modo.api,
            capturadoEm = agora,
        )
        escopo.launch {
            n.posicoes.guardar(posicao)
            // O primeiro ponto sobe na hora: o escritório não fica em "esperando o GPS".
            if (forcar) n.posicoes.enviar(atual.usuarioId)
        }
    }

    private fun reiniciarEnvio(atual: EstadoRastreio) {
        laco?.cancel()
        laco = escopo.launch {
            while (isActive) {
                delay(atual.modo.enviarACadaMs)
                repetirSeParado(atual)
                nucleo?.posicoes?.enviar(atual.usuarioId)
            }
        }
    }

    /** Parado, o iPhone não manda nada: a última posição conhecida vale de novo. */
    private fun repetirSeParado(atual: EstadoRastreio) {
        val ultimo = ultimoGuardadoMs ?: return
        if (Datas.agoraMs() - ultimo < atual.modo.guardarACadaMs) return
        val local = gerente?.location ?: return
        val p = ponto(local)
        guardar(atual, p.latitude, p.longitude, p.precisao?.toFloat(), 0f, forcar = false)
    }

    private fun bateria(): Int? {
        val nivel = UIDevice.currentDevice.batteryLevel
        if (nivel < 0f) return null
        return (nivel * 100).roundToInt().takeIf { it in 0..100 }
    }
}

private class DelegadoRastreio(private val aoLocalizar: (CLLocation) -> Unit) : NSObject(), CLLocationManagerDelegateProtocol {
    override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
        (didUpdateLocations.lastOrNull() as? CLLocation)?.let(aoLocalizar)
    }
}
