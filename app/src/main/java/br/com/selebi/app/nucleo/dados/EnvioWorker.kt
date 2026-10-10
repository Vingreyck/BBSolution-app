package br.com.selebi.app.nucleo.dados

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import br.com.selebi.app.SelebiApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Sobe a fila quando há rede — com o app aberto, fechado ou com o celular no
 * bolso. É o WorkManager quem espera o sinal voltar e quem tenta de novo
 * depois de uma queda, com intervalo crescente.
 */
class EnvioWorker(contexto: Context, parametros: WorkerParameters) : CoroutineWorker(contexto, parametros) {

    override suspend fun doWork(): Result {
        val nucleo = (applicationContext as SelebiApp).nucleo
        // Sem ninguém logado a fila espera: o login agenda o envio de novo.
        val usuario = nucleo.sessao.usuarioAtual() ?: return Result.success()
        val daFila = nucleo.fila.enviar(usuario.id)
        if (daFila == ResultadoEnvio.SEM_SESSAO) return Result.success()
        // As posições do rastreamento que ficaram sem sinal, independentes da
        // ordem das ações — sobem mesmo se algo da fila estiver esperando.
        val trajeto = nucleo.posicoes.enviar(usuario.id)
        return if (daFila == ResultadoEnvio.TENTAR_DE_NOVO || trajeto == ResultadoEnvio.TENTAR_DE_NOVO) Result.retry() else Result.success()
    }

    companion object {
        const val NOME = "envio-os"
    }
}

class AgendadorWorkManager(
    private val contexto: Context,
    private val escopo: CoroutineScope,
) : AgendadorEnvio {

    override fun agendar() {
        escopo.launch(Dispatchers.IO) {
            val trabalho = WorkManager.getInstance(contexto)
            // Já tem um envio esperando rede? Ele leva tudo o que estiver na fila
            // quando rodar — não precisa de outro.
            val infos = runCatching { trabalho.getWorkInfosForUniqueWork(EnvioWorker.NOME).get() }.getOrDefault(emptyList())
            if (infos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED }) return@launch

            val pedido = OneTimeWorkRequestBuilder<EnvioWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            // "Anexar": se um envio está rodando agora, este roda logo depois e
            // pega o que entrou na fila no meio do caminho.
            trabalho.enqueueUniqueWork(EnvioWorker.NOME, ExistingWorkPolicy.APPEND_OR_REPLACE, pedido)
        }
    }
}
