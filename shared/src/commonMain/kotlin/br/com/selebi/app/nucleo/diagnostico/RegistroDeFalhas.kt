package br.com.selebi.app.nucleo.diagnostico

import br.com.selebi.app.nucleo.plataforma.Ambiente
import br.com.selebi.app.nucleo.plataforma.descricaoDoDispositivo
import br.com.selebi.app.nucleo.plataforma.nomeDoSistema
import br.com.selebi.app.nucleo.rede.ErroApp
import br.com.selebi.app.nucleo.rede.ExecutorApi
import br.com.selebi.app.nucleo.rede.FalhaPedido
import br.com.selebi.app.nucleo.rede.Resultado
import br.com.selebi.app.nucleo.rede.SelebiApi
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.json.Json

/**
 * Quando o app trava, ele conta.
 *
 * No instante do travamento não há como falar com o servidor — o processo está
 * morrendo. Então o sistema avisa [gravar], que escreve a falha num arquivo, e na
 * próxima abertura o [enviarPendente] manda para o servidor, que encaminha ao
 * Sentry. Sem isso, um travamento no telhado só existia se o técnico contasse.
 *
 * Guarda uma falha só (a mais recente): se travar em laço, não enche o celular
 * nem o servidor. O texto leva a pilha e a mensagem do erro — **nada do que a
 * pessoa digitou ou viu na tela**, e o servidor não recebe CPF nem senha.
 */
class RegistroDeFalhas(pasta: String, private val json: Json) {

    private val arquivo = Path(pasta, "falha-pendente.json")

    /** Chamado pelo gancho do sistema, na hora do travamento: curto, sem rede e sem exceção. */
    fun gravar(erro: Throwable) {
        runCatching {
            val pedido = FalhaPedido(
                plataforma = nomeDoSistema.lowercase(),
                versao = Ambiente.versao,
                build = Ambiente.build,
                dispositivo = descricaoDoDispositivo(),
                tipo = erro::class.simpleName ?: "Erro",
                mensagem = (erro.message ?: "").take(MENSAGEM_MAXIMA),
                pilha = erro.stackTraceToString().take(PILHA_MAXIMA),
                quando = kotlin.time.Clock.System.now().toString(),
            )
            SystemFileSystem.sink(arquivo).buffered().use { it.writeString(json.encodeToString(FalhaPedido.serializer(), pedido)) }
        }
    }

    /** Há uma falha esperando para ser enviada? */
    fun temPendente(): Boolean = SystemFileSystem.exists(arquivo)

    /**
     * Manda a falha guardada e apaga o arquivo. Sem sinal, fica para a próxima
     * abertura; o servidor recusando (formato que ele não aceita) também apaga,
     * para não tentar para sempre algo que nunca vai passar.
     */
    suspend fun enviarPendente(api: SelebiApi, executor: ExecutorApi) {
        if (!temPendente()) return
        val pedido = runCatching {
            json.decodeFromString(FalhaPedido.serializer(), SystemFileSystem.source(arquivo).buffered().use { it.readString() })
        }.getOrNull()
        if (pedido == null) {
            apagar()
            return
        }
        when (val r = executor.executar { api.enviarFalha(pedido) }) {
            is Resultado.Ok -> apagar()
            is Resultado.Falha -> if ((r.erro as? ErroApp.Servidor)?.status in 400..499) apagar()
        }
    }

    private fun apagar() {
        runCatching { SystemFileSystem.delete(arquivo, mustExist = false) }
    }

    private companion object {
        const val MENSAGEM_MAXIMA = 500
        const val PILHA_MAXIMA = 7_000
    }
}
