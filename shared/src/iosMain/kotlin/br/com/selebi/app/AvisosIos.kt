package br.com.selebi.app

import br.com.selebi.app.nucleo.dados.EstadoSessao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * A ponte dos avisos (push) entre o Swift e o app.
 *
 * Quem fala com a Apple é o `AppDelegate` do Swift — pedir permissão, receber o
 * token do APNs, saber que o técnico tocou no aviso. Ele chama aqui
 * (`AvisosIos.shared` no Swift), e daqui segue o mesmo caminho do Android:
 * `nucleo.push`.
 */
@Suppress("unused")
object AvisosIos {

    /** O token do APNs, já em hexadecimal. */
    fun definirToken(token: String) {
        nucleoDoApp.push.definirToken(token)
    }

    /** O técnico tocou no aviso de uma OS. */
    fun abrirOs(id: String?) {
        nucleoDoApp.push.abrirOs(id)
    }

    /** Aviso chegou com o app aberto: a lista atualiza sem o técnico puxar. */
    fun chegouComAppAberto() {
        nucleoDoApp.escopo.launch { nucleoDoApp.push.aoReceberComAppAberto() }
    }

    /**
     * Roda [acao] (no fio principal) assim que houver alguém logado — é quando o
     * Swift pede a permissão de aviso. Pedir antes do login seria pedir a quem
     * ainda nem sabe o que o app faz, igual ao Android.
     */
    fun aoEntrar(acao: () -> Unit) {
        nucleoDoApp.escopo.launch {
            nucleoDoApp.sessao.estado.first { it is EstadoSessao.Logado }
            withContext(Dispatchers.Main) { acao() }
        }
    }
}
