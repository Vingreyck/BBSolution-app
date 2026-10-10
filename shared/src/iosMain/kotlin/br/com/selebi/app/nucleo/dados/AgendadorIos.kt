package br.com.selebi.app.nucleo.dados

import br.com.selebi.app.nucleo.Nucleo
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskInvalid
import kotlin.math.min

/**
 * Quem sobe a fila no iPhone.
 *
 * O iPhone não tem o WorkManager: aqui é o próprio app que envia — na hora,
 * quando a rede volta e de tempos em tempos enquanto houver algo esperando,
 * com intervalo crescente (30 s, 1 min, 2 min… até 15 min).
 *
 * Indo para o segundo plano no meio de um envio, o app pede ao iOS os
 * segundos que faltam para terminar. E durante o rastreamento em campo o app
 * segue vivo no bolso, então a fila continua subindo.
 *
 * Os pedidos de envio entram num canal de uma vaga só: dez fotos tiradas sem
 * sinal são um pedido, não dez.
 */
class AgendadorIos(private val nucleo: Nucleo) : AgendadorEnvio {
    private val pedidos = Channel<Unit>(Channel.CONFLATED)

    override fun agendar() {
        pedidos.trySend(Unit)
    }

    /** Começa a atender os pedidos. Chamado depois de o núcleo estar montado. */
    fun iniciar() {
        nucleo.escopo.launch {
            for (pedido in pedidos) enviarAteAcabar()
        }
        // Rede voltou: o que estava parado sobe.
        nucleo.escopo.launch {
            nucleo.conectividade.online.collect { online -> if (online) agendar() }
        }
    }

    private suspend fun enviarAteAcabar() {
        var espera = ESPERA_INICIAL
        while (true) {
            val resultado = comTempoDoSistema { enviarUmaVez() }
            if (resultado != ResultadoEnvio.TENTAR_DE_NOVO) return
            // Sem sinal: espera a rede voltar ou o tempo passar, o que vier antes.
            withTimeoutOrNull(espera) {
                delay(2_000)
                nucleo.conectividade.online.first { it }
            }
            espera = min(espera * 2, ESPERA_MAXIMA)
        }
    }

    private suspend fun enviarUmaVez(): ResultadoEnvio {
        // Sem ninguém logado a fila espera: o login agenda o envio de novo.
        val usuario = nucleo.sessao.usuarioAtual() ?: return ResultadoEnvio.SEM_SESSAO
        val daFila = nucleo.fila.enviar(usuario.id)
        if (daFila == ResultadoEnvio.SEM_SESSAO) return daFila
        // As posições do rastreamento sobem mesmo se algo da fila estiver esperando.
        val trajeto = nucleo.posicoes.enviar(usuario.id)
        return if (daFila == ResultadoEnvio.TENTAR_DE_NOVO || trajeto == ResultadoEnvio.TENTAR_DE_NOVO) {
            ResultadoEnvio.TENTAR_DE_NOVO
        } else {
            ResultadoEnvio.TUDO_ENVIADO
        }
    }

    /** Pede ao iOS para terminar o envio mesmo se o app for para o segundo plano no meio. */
    private suspend fun <T> comTempoDoSistema(bloco: suspend () -> T): T {
        val app = UIApplication.sharedApplication
        var tarefa = UIBackgroundTaskInvalid
        tarefa = app.beginBackgroundTaskWithExpirationHandler {
            app.endBackgroundTask(tarefa)
            tarefa = UIBackgroundTaskInvalid
        }
        try {
            return bloco()
        } finally {
            if (tarefa != UIBackgroundTaskInvalid) app.endBackgroundTask(tarefa)
        }
    }

    private companion object {
        const val ESPERA_INICIAL = 30_000L
        const val ESPERA_MAXIMA = 15 * 60_000L
    }
}
