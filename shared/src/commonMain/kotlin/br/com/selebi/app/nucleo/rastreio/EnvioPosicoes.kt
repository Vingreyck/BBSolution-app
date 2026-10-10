package br.com.selebi.app.nucleo.rastreio

import br.com.selebi.app.nucleo.util.Datas
import br.com.selebi.app.nucleo.banco.PosicaoDao
import br.com.selebi.app.nucleo.banco.PosicaoEntidade
import br.com.selebi.app.nucleo.dados.ResultadoEnvio
import br.com.selebi.app.nucleo.rede.ErroApp
import br.com.selebi.app.nucleo.rede.ExecutorApi
import br.com.selebi.app.nucleo.rede.PontoPedido
import br.com.selebi.app.nucleo.rede.PosicoesPedido
import br.com.selebi.app.nucleo.rede.Resultado
import br.com.selebi.app.nucleo.rede.SelebiApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant

/**
 * Sobe as posições guardadas, em lotes, das mais velhas para as mais novas.
 *
 * Sem sinal, elas ficam no banco e sobem quando a rede volta — pelo próprio
 * serviço de rastreamento, ou pelo envio em segundo plano da fila, se o
 * rastreamento já tiver terminado. Lote que o servidor recusa (nunca deveria)
 * é descartado: repetir um lote ruim travaria todos os seguintes.
 */
class EnvioPosicoes(
    private val dao: PosicaoDao,
    private val api: SelebiApi,
    private val executor: ExecutorApi,
) {
    private val trava = Mutex()

    suspend fun guardar(posicao: PosicaoEntidade): Long = dao.inserir(posicao)

    suspend fun enviar(usuarioId: String): ResultadoEnvio = trava.withLock {
        // O servidor recusa ponto de mais de 7 dias; não adianta carregar.
        dao.apagarVelhas(Datas.agoraMs() - 7 * 86_400_000L + 3_600_000L)
        var lote = dao.lote(usuarioId, LOTE)
        while (lote.isNotEmpty()) {
            val pedido = PosicoesPedido(lote.map(::paraPedido))
            when (val r = executor.executar { api.enviarPosicoes(pedido) }) {
                is Resultado.Ok -> dao.apagar(lote.map { it.id })
                is Resultado.Falha -> when (val e = r.erro) {
                    ErroApp.SemConexao, ErroApp.TempoEsgotado, is ErroApp.Inesperado -> return@withLock ResultadoEnvio.TENTAR_DE_NOVO
                    is ErroApp.Servidor -> when {
                        e.status == 401 -> return@withLock ResultadoEnvio.SEM_SESSAO
                        e.status == 408 || e.status == 429 || e.status >= 500 -> return@withLock ResultadoEnvio.TENTAR_DE_NOVO
                        else -> dao.apagar(lote.map { it.id })
                    }
                }
            }
            lote = dao.lote(usuarioId, LOTE)
        }
        ResultadoEnvio.TUDO_ENVIADO
    }

    /** Outra pessoa entrou neste celular: o trajeto da anterior não sobe em nome dela. */
    suspend fun descartarDeOutros(usuarioId: String) = dao.apagarDeOutros(usuarioId)

    private fun paraPedido(p: PosicaoEntidade) = PontoPedido(
        ordemId = p.ordemId,
        latitude = p.latitude,
        longitude = p.longitude,
        precisao = p.precisao?.toDouble(),
        velocidade = p.velocidade?.toDouble(),
        bateria = p.bateria,
        modo = p.modo,
        capturadoEm = Instant.fromEpochMilliseconds(p.capturadoEm).toString(),
    )

    private companion object {
        /** Menor que o teto do servidor (300): um lote de 4G fraco não pode demorar demais. */
        const val LOTE = 200
    }
}
