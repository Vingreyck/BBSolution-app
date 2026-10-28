package br.com.selebi.app.nucleo.dados

import br.com.selebi.app.nucleo.Nucleo
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskInvalid

/**
 * Quem sobe a fila no iPhone.
 *
 * O iPhone não tem o WorkManager: aqui é o próprio app que envia. Quando e com
 * que espera quem decide é o [LacoDeEnvio]; esta classe só liga o laço ao app —
 * a fila, os pontos do GPS e o tempo extra que o iOS dá ao app que vai para o
 * segundo plano no meio de um envio.
 *
 * E durante o rastreamento em campo o app segue vivo no bolso, então a fila
 * continua subindo.
 */
class AgendadorIos(private val nucleo: Nucleo) : AgendadorEnvio {
    // Preguiçoso: o núcleo ainda está sendo montado quando esta classe nasce.
    private val laco by lazy {
        LacoDeEnvio(
            escopo = nucleo.escopo,
            rede = nucleo.conectividade.online,
            envioUmaVez = { comTempoDoSistema { enviarUmaVez() } },
        )
    }

    override fun agendar() = laco.pedir()

    /** Começa a atender os pedidos. Chamado depois de o núcleo estar montado. */
    fun iniciar() = laco.iniciar()

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
}
