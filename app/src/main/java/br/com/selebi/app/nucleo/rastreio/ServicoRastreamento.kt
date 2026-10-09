package br.com.selebi.app.nucleo.rastreio

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import br.com.selebi.app.MainActivity
import br.com.selebi.app.R
import br.com.selebi.app.nucleo.banco.PosicaoEntidade
import br.com.selebi.app.nucleo.dados.AgendadorEnvio
import br.com.selebi.app.nucleo.dados.Localizacao
import br.com.selebi.app.nucleo.dados.ResultadoEnvio
import br.com.selebi.app.nucleo.modelo.Campo
import br.com.selebi.app.nucleo.modelo.ModoRastreio
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * O GPS do técnico em campo, com o app aberto, minimizado ou com a tela
 * apagada no bolso.
 *
 * É um serviço em primeiro plano com notificação fixa — o jeito que o Android
 * permite rastrear com o app fora da tela sem pedir localização "o tempo todo",
 * e o que deixa claro para o técnico que ele está sendo rastreado.
 *
 * Cada ponto vai para o banco na hora; o envio sobe em lotes (a cada 10 s a
 * caminho, a cada minuto no local). Sem sinal, os pontos esperam no banco. Se
 * o Android matar o serviço, ele volta sozinho (START_STICKY) e lê o que estava
 * fazendo na [MemoriaRastreio].
 */
@AndroidEntryPoint
class ServicoRastreamento : Service() {

    @Inject lateinit var memoria: MemoriaRastreio

    @Inject lateinit var envio: EnvioPosicoes

    @Inject lateinit var localizacao: Localizacao

    @Inject lateinit var agendador: AgendadorEnvio

    private val escopo = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var estado: EstadoRastreio? = null
    private var ultimoGuardadoMs: Long? = null
    private var ultimoEnvio: LocalTime? = null
    private var laco: Job? = null
    private val ouvinte = LocationListenerCompat { aoLocalizar(it) }
    private var ouvindo = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACAO_PARAR) {
            encerrar()
            return START_NOT_STICKY
        }
        val atual = memoria.ler()
        if (atual == null || !localizacao.temPermissao()) {
            // Quem chamou startForegroundService precisa ver o startForeground,
            // senão o Android derruba o app. Sobe e desce na hora.
            promover(atual)
            encerrar()
            return START_NOT_STICKY
        }
        val mudou = atual != estado
        estado = atual
        if (!promover(atual)) return START_NOT_STICKY

        if (intent != null && intent.hasExtra(EXTRA_LATITUDE)) {
            guardar(
                atual,
                intent.getDoubleExtra(EXTRA_LATITUDE, 0.0),
                intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0),
                if (intent.hasExtra(EXTRA_PRECISAO)) intent.getDoubleExtra(EXTRA_PRECISAO, 0.0).toFloat() else null,
                null,
                System.currentTimeMillis(),
                forcar = true,
            )
        }
        if (mudou || !ouvindo) {
            ouvir(atual.modo)
            reiniciarEnvio(atual)
        }
        return START_STICKY
    }

    /** Sobe para primeiro plano com a notificação. Falso se o Android recusou. */
    private fun promover(estado: EstadoRastreio?): Boolean = try {
        criarCanal()
        ServiceCompat.startForeground(
            this,
            NOTIFICACAO,
            notificacao(estado),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
        )
        true
    } catch (e: Exception) {
        // Android 14 recusa serviço de localização sem a permissão concedida.
        Log.w("ServicoRastreamento", "Não subiu para primeiro plano.", e)
        stopSelf()
        false
    }

    @SuppressLint("MissingPermission")
    private fun ouvir(modo: ModoRastreio) {
        val gerente = getSystemService(LocationManager::class.java) ?: return
        pararDeOuvir()
        val pedido = LocationRequestCompat.Builder(modo.intervaloMs)
            .setQuality(
                if (modo == ModoRastreio.ECO) LocationRequestCompat.QUALITY_BALANCED_POWER_ACCURACY else LocationRequestCompat.QUALITY_HIGH_ACCURACY,
            )
            .setMinUpdateIntervalMillis(modo.intervaloMs / 2)
            .build()
        val provedor = provedor(gerente, modo) ?: return
        try {
            LocationManagerCompat.requestLocationUpdates(gerente, provedor, pedido, ContextCompat.getMainExecutor(this), ouvinte)
            ouvindo = true
        } catch (e: SecurityException) {
            Log.w("ServicoRastreamento", "Sem permissão de localização.", e)
            encerrar()
        }
    }

    /**
     * O melhor provedor para o modo: o "fundido" do Android 12+ (mistura GPS,
     * Wi-Fi e antena e gasta menos); antes disso, GPS a caminho e rede no
     * local — com o outro de reserva se um estiver desligado.
     */
    private fun provedor(gerente: LocationManager, modo: ModoRastreio): String? {
        val ligados = runCatching { gerente.getProviders(true) }.getOrDefault(emptyList())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && LocationManager.FUSED_PROVIDER in ligados) {
            return LocationManager.FUSED_PROVIDER
        }
        val preferido = if (modo == ModoRastreio.ECO) LocationManager.NETWORK_PROVIDER else LocationManager.GPS_PROVIDER
        return listOf(preferido, LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).firstOrNull { it in ligados }
    }

    private fun pararDeOuvir() {
        if (!ouvindo) return
        getSystemService(LocationManager::class.java)?.let { LocationManagerCompat.removeUpdates(it, ouvinte) }
        ouvindo = false
    }

    private fun aoLocalizar(local: Location) {
        val atual = estado ?: return
        // Desligado por fora (a OS saiu de campo, a pessoa saiu do app): para aqui também.
        if (memoria.ler()?.ordemId != atual.ordemId) {
            encerrar()
            return
        }
        guardar(
            atual,
            local.latitude,
            local.longitude,
            if (local.hasAccuracy()) local.accuracy else null,
            if (local.hasSpeed()) local.speed else null,
            System.currentTimeMillis(),
        )
    }

    private fun guardar(
        atual: EstadoRastreio,
        latitude: Double,
        longitude: Double,
        precisao: Float?,
        velocidade: Float?,
        agoraMs: Long,
        forcar: Boolean = false,
    ) {
        if (!forcar && !Campo.deveGuardar(ultimoGuardadoMs, agoraMs, precisao, atual.modo)) return
        ultimoGuardadoMs = agoraMs
        val ponto = PosicaoEntidade(
            usuarioId = atual.usuarioId,
            ordemId = atual.ordemId,
            latitude = latitude,
            longitude = longitude,
            precisao = precisao,
            velocidade = velocidade,
            bateria = bateria(),
            modo = atual.modo.api,
            capturadoEm = agoraMs,
        )
        escopo.launch {
            envio.guardar(ponto)
            // O primeiro ponto sobe na hora: o escritório não fica em "esperando o GPS".
            if (forcar) enviarAgora(atual)
        }
    }

    private fun reiniciarEnvio(atual: EstadoRastreio) {
        laco?.cancel()
        laco = escopo.launch {
            while (isActive) {
                delay(atual.modo.enviarACadaMs)
                enviarAgora(atual)
            }
        }
    }

    private suspend fun enviarAgora(atual: EstadoRastreio) {
        if (envio.enviar(atual.usuarioId) == ResultadoEnvio.TUDO_ENVIADO) {
            ultimoEnvio = LocalTime.now()
            atualizarNotificacao()
        }
    }

    private fun bateria(): Int? =
        getSystemService(BatteryManager::class.java)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            ?.takeIf { it in 0..100 }

    /** Desliga tudo. O que não subiu fica no banco, e o envio em segundo plano leva quando der. */
    private fun encerrar() {
        pararDeOuvir()
        laco?.cancel()
        estado = null
        agendador.agendar()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        pararDeOuvir()
        escopo.cancel()
        super.onDestroy()
    }

    // --- notificação -----------------------------------------------------------

    private fun criarCanal() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val canal = NotificationChannel(CANAL, "Em campo", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Aparece enquanto o BBSolution mostra a sua posição ao escritório, entre o \"Estou a caminho\" e o fim da OS."
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(canal)
    }

    private fun notificacao(estado: EstadoRastreio?): android.app.Notification {
        val abrir = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val numero = estado?.numero?.let { "#" + it.toString().padStart(4, '0') }.orEmpty()
        val cliente = estado?.cliente?.substringBefore(" (")?.trim().orEmpty()
        val texto = when (estado?.modo) {
            ModoRastreio.DESLOCAMENTO -> "A caminho de $cliente"
            ModoRastreio.ECO -> "No local de $cliente · economia de bateria"
            null -> "Localização"
        }
        return NotificationCompat.Builder(this, CANAL)
            .setSmallIcon(R.drawable.ic_notificacao_campo)
            .setContentTitle("Em campo · OS $numero")
            .setContentText(texto)
            .setSubText(ultimoEnvio?.let { "posição enviada às " + it.format(HORA) })
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(abrir)
            .build()
    }

    /** Sem a permissão de notificação (Android 13+), o Android só não mostra — não quebra nada. */
    @SuppressLint("MissingPermission")
    private fun atualizarNotificacao() {
        val gerente = getSystemService(NotificationManager::class.java) ?: return
        runCatching { gerente.notify(NOTIFICACAO, notificacao(estado)) }
    }

    companion object {
        const val ACAO_INICIAR = "br.com.selebi.app.rastreio.INICIAR"
        const val ACAO_PARAR = "br.com.selebi.app.rastreio.PARAR"
        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
        const val EXTRA_PRECISAO = "precisao"
        private const val CANAL = "em_campo"
        private const val NOTIFICACAO = 4701
        private val HORA = DateTimeFormatter.ofPattern("HH:mm")
    }
}
