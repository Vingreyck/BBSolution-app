package br.com.selebi.app.nucleo.push

import br.com.selebi.app.nucleo.dados.EstadoSessao
import br.com.selebi.app.nucleo.dados.OrdensRepository
import br.com.selebi.app.nucleo.plataforma.nomeDoSistema
import br.com.selebi.app.nucleo.rede.AparelhoPedido
import br.com.selebi.app.nucleo.rede.ExecutorApi
import br.com.selebi.app.nucleo.rede.Resultado
import br.com.selebi.app.nucleo.rede.SelebiApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Os avisos (push) de OS atribuída, agendada ou cancelada.
 *
 * O sistema do celular entrega o token (FCM no Android) e este objeto o conta
 * ao servidor assim que há alguém logado — nunca antes, porque o servidor liga
 * o token à sessão. Se o servidor não responder, tenta de novo; o técnico sem
 * sinal só fica sem aviso, e a lista de OS continua chegando pelo caminho de
 * sempre quando o sinal volta.
 *
 * Tocar no aviso entrega o id da OS por [abrirOs]; a navegação o consome.
 */
class AvisosPush(
    private val api: SelebiApi,
    private val executor: ExecutorApi,
    private val ordens: OrdensRepository,
    sessao: StateFlow<EstadoSessao>,
    escopo: CoroutineScope,
    private val esperaEntreTentativas: Long = 60_000,
) {
    private val token = MutableStateFlow<String?>(null)
    private val _osParaAbrir = MutableStateFlow<String?>(null)

    /** A OS que o técnico tocou no aviso e ainda não foi aberta na tela. */
    val osParaAbrir: StateFlow<String?> = _osParaAbrir.asStateFlow()

    /** Quem está logado e já pode usar a API (senha provisória barra a rota). */
    private val quemPodeRegistrar: Flow<String?> = sessao
        .map { (it as? EstadoSessao.Logado)?.usuario?.takeUnless { u -> u.deveTrocarSenha }?.id }
        .distinctUntilChanged()

    init {
        escopo.launch {
            combine(quemPodeRegistrar, token) { usuario, t -> if (usuario != null) t else null }
                .distinctUntilChanged()
                .collect { t -> if (t != null) registrar(t) }
        }
        // Saiu de vez: o aviso que ficou pendente era da pessoa anterior.
        escopo.launch {
            sessao.collect { if (it is EstadoSessao.Deslogado) _osParaAbrir.value = null }
        }
    }

    /** O sistema deu (ou trocou) o token deste celular. */
    fun definirToken(novo: String?) {
        token.value = novo?.takeIf { it.isNotBlank() }
    }

    /** O técnico tocou no aviso de uma OS. */
    fun abrirOs(id: String?) {
        if (!id.isNullOrBlank()) _osParaAbrir.value = id
    }

    fun osAberta() {
        _osParaAbrir.value = null
    }

    /** Chegou aviso com o app aberto: traz a lista nova sem esperar o técnico puxar. */
    suspend fun aoReceberComAppAberto() {
        ordens.sincronizar()
    }

    private suspend fun registrar(token: String) {
        repeat(5) {
            val resultado = executor.executar {
                api.registrarAparelho(AparelhoPedido(token, nomeDoSistema.lowercase()))
            }
            if (resultado is Resultado.Ok) return
            delay(esperaEntreTentativas)
        }
    }
}
