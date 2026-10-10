package br.com.selebi.app.nucleo.rede

import kotlinx.coroutines.flow.Flow

/** Um arquivo para subir: a foto ou a assinatura guardada no celular. */
class ArquivoEnvio(val caminho: String, val nome: String, val tipo: String)

/** Um evento do fluxo ao vivo (Server-Sent Events): o `event:` e o `data:`. */
data class EventoSse(val tipo: String?, val dados: String)

/**
 * As rotas de `src/app/api/app/v1` do Selebi.
 *
 * Toda resposta de erro do servidor vira [ErroHttp]; falta de sinal vira a
 * exceção de rede do sistema. Quem chama passa pelo [ExecutorApi], que
 * transforma as duas coisas em [Resultado].
 */
interface SelebiApi {
    suspend fun resolverEmpresa(pedido: CodigoEmpresaPedido): EmpresaDto

    suspend fun cadastrar(pedido: CadastroPedido): CadastroResposta

    suspend fun entrar(pedido: LoginPedido): LoginResposta

    suspend fun sair()

    suspend fun eu(): EuResposta

    suspend fun trocarSenha(pedido: TrocarSenhaPedido): EuResposta

    suspend fun excluirConta(pedido: ExcluirContaPedido): ExclusaoResposta

    suspend fun ordens(): OrdensResposta

    suspend fun ordem(id: String): OrdemResposta

    // Execução da OS. Cada chamada devolve a OS como o servidor a deixou.

    suspend fun acao(id: String, pedido: AcaoPedido): AcaoResposta

    suspend fun responder(id: String, itemId: String, pedido: RespostaPedido): RespostaItemResposta

    /** Campo nulo não vai no formulário — o servidor entende como ausente. */
    suspend fun anexar(
        id: String,
        arquivo: ArquivoEnvio,
        idCliente: String,
        categoria: String,
        itemId: String?,
        capturadoEm: String?,
        latitude: String?,
        longitude: String?,
    ): AnexoResposta

    /** Tira uma foto já enviada — o arquivo vai para a lixeira do Drive. */
    suspend fun removerAnexo(id: String, anexoId: String): OrdemTalvezResposta

    /** A foto de um anexo, para mostrar a que não foi tirada neste celular. */
    suspend fun baixarAnexo(id: String, anexoId: String): ByteArray

    suspend fun anotar(id: String, pedido: NotaPedido): NotaResposta

    // Rastreamento em campo.

    suspend fun enviarPosicoes(pedido: PosicoesPedido): PosicoesResposta

    suspend fun acompanhamento(): AcompanhamentoResposta

    /** As posições ao vivo. Termina quando a conexão cai — quem ouve reconecta. */
    fun acompanhamentoAoVivo(): Flow<EventoSse>

    suspend fun trilha(id: String): TrilhaDto

    suspend fun equipe(): EquipeResposta

    suspend fun aprovar(id: String, pedido: AprovarPedido)

    suspend fun recusar(id: String)

    suspend fun alterarMembro(id: String, pedido: AlterarMembroPedido)

    suspend fun redefinirSenha(id: String): SenhaProvisoriaResposta
}
