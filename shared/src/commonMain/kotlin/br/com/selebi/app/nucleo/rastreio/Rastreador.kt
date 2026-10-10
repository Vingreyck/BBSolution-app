package br.com.selebi.app.nucleo.rastreio

import br.com.selebi.app.nucleo.dados.Localizacao
import br.com.selebi.app.nucleo.dados.Ponto
import br.com.selebi.app.nucleo.modelo.ModoRastreio
import br.com.selebi.app.nucleo.modelo.Ordem
import br.com.selebi.app.nucleo.modelo.StatusOs
import br.com.selebi.app.nucleo.modelo.Usuario
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Quem está sendo rastreado, em qual OS, e como. Sobrevive a o Android matar o app. */
data class EstadoRastreio(
    val usuarioId: String,
    val ordemId: String,
    val numero: Int,
    val cliente: String,
    val modo: ModoRastreio,
)

/**
 * O GPS de cada sistema: no Android, um serviço em primeiro plano com
 * notificação fixa; no iPhone, o `CLLocationManager` com a faixa azul de
 * "localização em uso". Os dois leem a [MemoriaRastreio] para saber o que
 * rastrear e param sozinhos quando ela é apagada.
 */
interface MotorRastreio {
    /** Liga (ou troca o modo). `primeiro` é um ponto que já vai na hora. */
    fun ligar(estado: EstadoRastreio, primeiro: Ponto?)

    fun desligar()
}

/**
 * Liga e desliga o GPS do técnico conforme a OS — a regra do SeeNet:
 *
 * - **"Estou a caminho"** liga, no modo forte (a cada 5 s).
 * - **"Cheguei"** (ou "Retomar") passa para o modo econômico: o escritório
 *   continua vendo o técnico no local até o fim, gastando pouca bateria.
 * - **Concluir, pausar ou "não consegui atender"** desliga.
 *
 * Fora disso, ninguém é rastreado. E rastreamento é sempre visível: no
 * Android, notificação fixa ("Em campo · OS #0049"); no iPhone, a faixa azul.
 */
class Rastreador(
    private val memoria: MemoriaRastreio,
    private val localizacao: Localizacao,
    private val motor: MotorRastreio,
) {
    private val _ativo = MutableStateFlow(memoria.ler())
    val ativo: StateFlow<EstadoRastreio?> = _ativo.asStateFlow()

    /**
     * Liga (ou troca o modo). `primeiro` é o ponto que a ação acabou de pegar:
     * vai já, para o escritório não ficar em "esperando o GPS" — a lição do
     * SeeNet, onde o primeiro ponto do fluxo demorava uns 10 s.
     */
    fun iniciar(estado: EstadoRastreio, primeiro: Ponto? = null) {
        // Sem permissão o GPS nem liga: o Android 14 derruba serviço de
        // localização sem ela. A ação da OS segue normal, só sem o mapa.
        if (!localizacao.temPermissao()) return
        memoria.salvar(estado)
        _ativo.value = estado
        motor.ligar(estado, primeiro)
    }

    /** Desliga. Com `ordemId`, só se for esta OS que está sendo rastreada. */
    fun parar(ordemId: String? = null) {
        val atual = memoria.ler() ?: return
        if (ordemId != null && atual.ordemId != ordemId) return
        memoria.apagar()
        _ativo.value = null
        motor.desligar()
    }

    /**
     * Confere contra as OS do celular, depois de cada sincronização e ao abrir
     * o app: retoma o rastreamento que o Android interrompeu, troca o modo se a
     * OS mudou (o escritório pode ter mexido), e desliga se a OS saiu de campo
     * ou foi para outra pessoa.
     */
    fun conferir(ordens: List<Ordem>, usuario: Usuario?) {
        if (usuario == null) {
            parar()
            return
        }
        val atual = memoria.ler()
        if (atual != null && atual.usuarioId != usuario.id) parar()

        val emCampo = ordens.filter { it.responsavel?.id == usuario.id && ModoRastreio.para(it.status) != null }
        val rastreada = memoria.ler()?.let { r -> emCampo.firstOrNull { it.id == r.ordemId } }
        // A caminho vem antes de atendendo: é o deslocamento que o escritório quer ver.
        val escolhida = rastreada
            ?: emCampo.firstOrNull { it.status == StatusOs.EM_DESLOCAMENTO }
            ?: emCampo.firstOrNull()

        if (escolhida == null) {
            parar()
            return
        }
        iniciar(
            EstadoRastreio(
                usuarioId = usuario.id,
                ordemId = escolhida.id,
                numero = escolhida.numero,
                cliente = escolhida.cliente.nome,
                modo = ModoRastreio.para(escolhida.status)!!,
            ),
        )
    }
}
