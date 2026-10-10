package br.com.selebi.app.nucleo.dados

import br.com.selebi.app.nucleo.rede.UsuarioDto
import kotlinx.serialization.Serializable

@Serializable
data class SessaoSalva(val token: String, val usuario: UsuarioDto)

/**
 * Onde a sessão sobrevive a fechar o app: token e perfil, guardados de forma
 * que só este aparelho abre — no Android, cifrados com a chave do Keystore;
 * no iPhone, no Keychain.
 *
 * Qualquer falha ao abrir vira "não há sessão" e o técnico entra de novo.
 * Nunca vira o app fechando sozinho.
 */
interface CofreSessao {
    suspend fun ler(): SessaoSalva?

    suspend fun salvar(sessao: SessaoSalva)

    suspend fun apagar()
}
