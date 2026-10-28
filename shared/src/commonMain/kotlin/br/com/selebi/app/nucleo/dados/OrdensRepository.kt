package br.com.selebi.app.nucleo.dados

import br.com.selebi.app.nucleo.banco.OrdemDao
import br.com.selebi.app.nucleo.banco.paraModelo
import br.com.selebi.app.nucleo.modelo.Ordem
import br.com.selebi.app.nucleo.modelo.Pessoa
import br.com.selebi.app.nucleo.rede.ErroApp
import br.com.selebi.app.nucleo.rede.ExecutorApi
import br.com.selebi.app.nucleo.rede.Resultado
import br.com.selebi.app.nucleo.rede.SelebiApi
import br.com.selebi.app.nucleo.util.Datas
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant

/**
 * As ordens de serviço: a tela lê do banco local, a rede só atualiza o banco.
 *
 * Sincronizar é substituir a cópia local pela lista do servidor — com a fila
 * de envio reaplicada por cima (ver [CopiaLocal]). Se a rede falhar, nada muda
 * na tela: o técnico continua vendo a última lista boa, e a faixa de "sem
 * conexão" diz de quando ela é.
 */
class OrdensRepository(
    private val api: SelebiApi,
    private val executor: ExecutorApi,
    private val dao: OrdemDao,
    private val copia: CopiaLocal,
    private val fila: FilaEnvio,
    private val preferencias: PreferenciasApp,
) {
    fun observarTodas(): Flow<List<Ordem>> =
        dao.observarTodas().map { lista -> lista.map { it.paraModelo() } }

    fun observar(id: String): Flow<Ordem?> = dao.observar(id).map { it?.paraModelo() }

    val sincronizadoEm: Flow<Instant?> = preferencias.sincronizadoEm

    suspend fun sincronizar(): Resultado<Unit> {
        // Rede boa para buscar é rede boa para subir o que estiver parado.
        fila.agendar()
        val resultado = executor.executar { api.ordens() }
        if (resultado !is Resultado.Ok) return resultado as Resultado.Falha

        copia.gravarTodas(resultado.valor.ordens)
        preferencias.marcarSincronizacao(Datas.lerInstante(resultado.valor.geradoEm) ?: Datas.agoraMesmo())
        // A lista de quem pode voltar numa visita de volta viaja junto: a vistoria
        // acaba onde o sinal falha, e a lista precisa já estar no celular.
        atualizarTecnicos()
        return Resultado.Ok(Unit)
    }

    /** Os técnicos que podem ser escolhidos para uma visita de volta (a última lista que o celular viu). */
    val tecnicos: Flow<List<Pessoa>> = preferencias.tecnicos.map { lista -> lista.map { (id, nome) -> Pessoa(id, nome) } }

    /** Busca a lista de técnicos; sem sinal, fica a que já estava. */
    suspend fun atualizarTecnicos() {
        val r = executor.executar { api.tecnicos() }
        if (r is Resultado.Ok) preferencias.guardarTecnicos(r.valor.tecnicos.map { it.id to it.nome })
    }

    /** Atualiza uma OS só — a tela de detalhe chama ao abrir. */
    suspend fun atualizar(id: String): Resultado<Unit> {
        val resultado = executor.executar { api.ordem(id) }
        return when {
            resultado is Resultado.Ok -> {
                copia.gravarUma(resultado.valor.ordem)
                Resultado.Ok(Unit)
            }
            // Saiu do escopo desta pessoa (reatribuída, apagada): some daqui também.
            resultado is Resultado.Falha && (resultado.erro as? ErroApp.Servidor)?.status == 404 -> {
                copia.esquecer(id)
                resultado
            }
            else -> resultado as Resultado.Falha
        }
    }
}
