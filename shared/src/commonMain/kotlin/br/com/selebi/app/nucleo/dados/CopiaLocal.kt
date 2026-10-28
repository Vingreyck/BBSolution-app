package br.com.selebi.app.nucleo.dados

import br.com.selebi.app.nucleo.banco.emTransacao
import br.com.selebi.app.nucleo.arquivos.ArquivosOs
import br.com.selebi.app.nucleo.banco.AnexoEntidade
import br.com.selebi.app.nucleo.banco.NotaEntidade
import br.com.selebi.app.nucleo.banco.OrdemDao
import br.com.selebi.app.nucleo.banco.PendenteDao
import br.com.selebi.app.nucleo.banco.PendenteEntidade
import br.com.selebi.app.nucleo.banco.SelebiBanco
import br.com.selebi.app.nucleo.banco.anexosParaEntidade
import br.com.selebi.app.nucleo.banco.itensParaEntidade
import br.com.selebi.app.nucleo.banco.notasParaEntidade
import br.com.selebi.app.nucleo.banco.paraEntidade
import br.com.selebi.app.nucleo.banco.paraModelo
import br.com.selebi.app.nucleo.modelo.RegrasOs
import br.com.selebi.app.nucleo.modelo.StatusOs
import br.com.selebi.app.nucleo.rede.AcaoPedido
import br.com.selebi.app.nucleo.rede.NotaPedido
import br.com.selebi.app.nucleo.rede.OrdemDto
import br.com.selebi.app.nucleo.rede.RespostaPedido
import br.com.selebi.app.nucleo.rede.paraResposta
import br.com.selebi.app.nucleo.rede.paraTexto
import br.com.selebi.app.nucleo.rede.respostaDoTexto
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.datetime.Instant

/** O que cada linha da fila é. */
object TipoPendente {
    const val ACAO = "acao"
    const val RESPOSTA = "resposta"
    const val ANEXO = "anexo"
    const val NOTA = "nota"
}

/** O que a fila guarda de uma foto, além do arquivo. */
@Serializable
data class AnexoNaFila(
    val categoria: String,
    val itemId: String? = null,
    val capturadoEm: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
)

/**
 * A cópia das OS no celular: o que o servidor mandou, com o que o técnico
 * fez e ainda não subiu aplicado por cima.
 *
 * Toda gravação vinda do servidor termina reaplicando a fila, na mesma
 * transação. Sem isso, a lista que chega enquanto uma foto espera sinal
 * "desfaria" o item verde, e o técnico acharia que perdeu a foto.
 *
 * Qual cópia vale é decidido pelo carimbo `atualizadoEm` do servidor: a lista
 * pedida antes de uma resposta subir chega depois dela, trazendo a OS velha,
 * e não pode passar por cima da nova.
 */
class CopiaLocal(
    private val banco: SelebiBanco,
    private val dao: OrdemDao,
    private val pendentes: PendenteDao,
    private val arquivos: ArquivosOs,
    private val json: Json,
) {
    /** A lista inteira do servidor. OS que não veio saiu do escopo e some daqui. */
    suspend fun gravarTodas(ordens: List<OrdemDto>) {
        banco.emTransacao {
            val locais = dao.versoes().associate { it.id to it.atualizadoEm }
            val (manter, novas) = ordens.partition { localMaisNova(locais[it.id], it.atualizadoEm) }
            val ficam = manter.map { it.id }.toSet()
            for (id in locais.keys - ficam) esquecerSemTransacao(id)
            novas.forEach { gravarSemTransacao(it) }
            reaplicar(null)
        }
        faxina()
    }

    /** Uma OS, como o servidor a devolveu depois de uma ação ou de abrir o detalhe. */
    suspend fun gravarUma(ordem: OrdemDto) {
        banco.emTransacao {
            val local = dao.versoes().firstOrNull { it.id == ordem.id }?.atualizadoEm
            if (localMaisNova(local, ordem.atualizadoEm)) return@emTransacao
            esquecerSemTransacao(ordem.id)
            gravarSemTransacao(ordem)
            reaplicar(ordem.id)
        }
    }

    /** A OS saiu do escopo desta pessoa (reatribuída, apagada). */
    suspend fun esquecer(ordemId: String) {
        banco.emTransacao { esquecerSemTransacao(ordemId) }
    }

    /**
     * O que o técnico acabou de fazer, aplicado na cópia local — a tela
     * responde na hora, com ou sem sinal. É o espelho de `src/os/` do
     * servidor; a resposta dele corrige se os dois discordarem.
     */
    suspend fun aplicar(p: PendenteEntidade) {
        when (p.tipo) {
            TipoPendente.ACAO -> {
                val pedido = json.decodeFromString(AcaoPedido.serializer(), p.corpoJson)
                val quando = pedido.ocorridoEm ?: Instant.fromEpochMilliseconds(p.criadoEm).toString()
                when (pedido.acao) {
                    "deslocamento" -> dao.mudarStatus(p.ordemId, StatusOs.EM_DESLOCAMENTO.api)
                    "iniciar" -> dao.iniciarLocal(p.ordemId, StatusOs.EM_ANDAMENTO.api, quando)
                    "pausar" -> dao.mudarStatus(p.ordemId, StatusOs.AGUARDANDO_PECA.api)
                    "retomar" -> dao.mudarStatus(p.ordemId, StatusOs.EM_ANDAMENTO.api)
                    "concluir" -> dao.concluirLocal(
                        p.ordemId,
                        StatusOs.CONCLUIDA.api,
                        pedido.resultado ?: "resolvido",
                        pedido.laudo,
                        pedido.assinaturaNome,
                        quando,
                    )
                    "nao_atendida" -> dao.mudarStatusSemData(p.ordemId, StatusOs.ABERTA.api)
                }
            }
            TipoPendente.RESPOSTA -> {
                val itemId = p.itemId ?: return
                val item = dao.buscarItem(itemId) ?: return
                val pedido = json.decodeFromString(RespostaPedido.serializer(), p.corpoJson)
                val valor = pedido.valor.paraResposta()
                // Observação ausente mantém a que está; vazia apaga — como no servidor.
                val observacao = when (val o = pedido.observacao) {
                    null -> item.observacao
                    else -> o.trim().ifEmpty { null }
                }
                // `agendar` ausente mantém como está, como no servidor.
                val agendar = pedido.agendar ?: item.agendar
                val cumprido = RegrasOs.cumprido(item.paraModelo(), valor, dao.contarFotos(itemId), observacao, agendar)
                dao.responderLocal(
                    itemId,
                    valor.paraTexto(),
                    observacao,
                    cumprido,
                    agendar,
                    pedido.respondidoEm ?: Instant.fromEpochMilliseconds(p.criadoEm).toString(),
                )
            }
            TipoPendente.ANEXO -> {
                val dados = json.decodeFromString(AnexoNaFila.serializer(), p.corpoJson)
                dao.salvarAnexos(
                    listOf(
                        AnexoEntidade(
                            id = p.idCliente,
                            ordemId = p.ordemId,
                            itemId = dados.itemId,
                            categoria = dados.categoria,
                            capturadoEm = dados.capturadoEm,
                            arquivoLocal = p.arquivo,
                            pendente = true,
                            enviadoPorId = p.usuarioId,
                        ),
                    ),
                )
                dados.itemId?.let { recalcular(it) }
            }
            TipoPendente.NOTA -> {
                val pedido = json.decodeFromString(NotaPedido.serializer(), p.corpoJson)
                dao.salvarNotas(
                    listOf(NotaEntidade(p.idCliente, p.ordemId, pedido.texto, p.usuarioId, null, pedido.criadoEm, pendente = true)),
                )
            }
        }
    }

    /** O item cumprido de novo, depois que uma foto entra ou sai. */
    suspend fun recalcular(itemId: String) {
        val item = dao.buscarItem(itemId) ?: return
        val ok = RegrasOs.cumprido(item.paraModelo(), respostaDoTexto(item.valorJson), dao.contarFotos(itemId))
        if (ok != item.concluido) dao.marcarCumprido(itemId, ok)
    }

    // -------------------------------------------------------------------------

    private suspend fun gravarSemTransacao(ordem: OrdemDto) {
        dao.salvarOrdens(listOf(ordem.paraEntidade()))
        dao.salvarItens(ordem.itensParaEntidade())
        dao.salvarAnexos(ordem.anexosParaEntidade(arquivos::arquivoDe))
        dao.salvarNotas(ordem.notasParaEntidade())
    }

    private suspend fun esquecerSemTransacao(ordemId: String) {
        dao.apagar(ordemId) // os itens vão junto (chave estrangeira)
        dao.apagarAnexosDoServidorDa(ordemId)
        dao.apagarNotasDoServidorDa(ordemId)
    }

    /** A fila por cima, na ordem em que aconteceu. Recusado não entra: o servidor já disse não. */
    private suspend fun reaplicar(ordemId: String?) {
        val fila = if (ordemId == null) pendentes.todos() else pendentes.daOrdem(ordemId)
        fila.filter { it.erro == null }.forEach { aplicar(it) }
    }

    private suspend fun faxina() {
        val emUso = dao.arquivosLocais().toSet() + pendentes.todos().mapNotNull { it.arquivo }
        val vivas = dao.ids().toSet() + pendentes.todos().map { it.ordemId }
        arquivos.faxina(vivas, emUso)
    }

    companion object {
        /**
         * A cópia daqui é mais nova que a que chegou? Os carimbos são ISO do
         * servidor, no mesmo formato — dá para comparar como texto.
         */
        fun localMaisNova(local: String?, chegou: String?): Boolean =
            local != null && chegou != null && local > chegou
    }
}
