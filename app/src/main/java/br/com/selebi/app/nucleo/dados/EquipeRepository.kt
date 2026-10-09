package br.com.selebi.app.nucleo.dados

import br.com.selebi.app.nucleo.modelo.Equipe
import br.com.selebi.app.nucleo.modelo.Papel
import br.com.selebi.app.nucleo.rede.AlterarMembroPedido
import br.com.selebi.app.nucleo.rede.AprovarPedido
import br.com.selebi.app.nucleo.rede.ExecutorApi
import br.com.selebi.app.nucleo.rede.Resultado
import br.com.selebi.app.nucleo.rede.SelebiApi
import br.com.selebi.app.nucleo.rede.exigirSucesso
import br.com.selebi.app.nucleo.rede.paraModelo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A equipe da empresa, para o administrador.
 *
 * Sem cópia local de propósito: aprovar alguém ou trocar um papel precisa do
 * servidor na hora, e decisão tomada sobre uma lista velha é pior que decisão
 * nenhuma.
 */
@Singleton
class EquipeRepository @Inject constructor(
    private val api: SelebiApi,
    private val executor: ExecutorApi,
) {
    suspend fun carregar(): Resultado<Equipe> {
        val resultado = executor.executar { api.equipe() }
        return when (resultado) {
            is Resultado.Ok -> Resultado.Ok(
                Equipe(
                    pendentes = resultado.valor.pendentes.map { it.paraModelo() },
                    membros = resultado.valor.membros.map { it.paraModelo() },
                ),
            )
            is Resultado.Falha -> resultado
        }
    }

    suspend fun aprovar(id: String, papel: Papel): Resultado<Unit> =
        executor.executar { api.aprovar(id, AprovarPedido(papel.api)).exigirSucesso() }

    suspend fun recusar(id: String): Resultado<Unit> =
        executor.executar { api.recusar(id).exigirSucesso() }

    suspend fun trocarPapel(id: String, papel: Papel): Resultado<Unit> =
        executor.executar { api.alterarMembro(id, AlterarMembroPedido(papel = papel.api)).exigirSucesso() }

    suspend fun definirAtivo(id: String, ativo: Boolean): Resultado<Unit> =
        executor.executar { api.alterarMembro(id, AlterarMembroPedido(ativo = ativo)).exigirSucesso() }

    /** A senha provisória, para mostrar uma vez ao administrador. */
    suspend fun redefinirSenha(id: String): Resultado<String> {
        val resultado = executor.executar { api.redefinirSenha(id) }
        return when (resultado) {
            is Resultado.Ok -> Resultado.Ok(resultado.valor.senhaProvisoria)
            is Resultado.Falha -> resultado
        }
    }
}
