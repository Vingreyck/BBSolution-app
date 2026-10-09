package br.com.selebi.app.nucleo.rede

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Streaming

/** As rotas de `src/app/api/app/v1` do Selebi. */
interface SelebiApi {
    @POST("empresa")
    suspend fun resolverEmpresa(@Body pedido: CodigoEmpresaPedido): EmpresaDto

    @POST("cadastro")
    suspend fun cadastrar(@Body pedido: CadastroPedido): CadastroResposta

    @POST("sessao")
    suspend fun entrar(@Body pedido: LoginPedido): LoginResposta

    @DELETE("sessao")
    suspend fun sair(): Response<Unit>

    @GET("eu")
    suspend fun eu(): EuResposta

    @POST("eu/senha")
    suspend fun trocarSenha(@Body pedido: TrocarSenhaPedido): EuResposta

    @POST("eu/excluir")
    suspend fun excluirConta(@Body pedido: ExcluirContaPedido): ExclusaoResposta

    @GET("ordens")
    suspend fun ordens(): OrdensResposta

    @GET("ordens/{id}")
    suspend fun ordem(@Path("id") id: String): OrdemResposta

    // Execução da OS. Cada chamada devolve a OS como o servidor a deixou.

    @POST("ordens/{id}/acoes")
    suspend fun acao(@Path("id") id: String, @Body pedido: AcaoPedido): AcaoResposta

    @PUT("ordens/{id}/itens/{itemId}")
    suspend fun responder(
        @Path("id") id: String,
        @Path("itemId") itemId: String,
        @Body pedido: RespostaPedido,
    ): RespostaItemResposta

    /** Campo nulo não vai no formulário — o servidor entende como ausente. */
    @Multipart
    @POST("ordens/{id}/anexos")
    suspend fun anexar(
        @Path("id") id: String,
        @Part arquivo: MultipartBody.Part,
        @Part("idCliente") idCliente: RequestBody,
        @Part("categoria") categoria: RequestBody,
        @Part("itemId") itemId: RequestBody?,
        @Part("capturadoEm") capturadoEm: RequestBody?,
        @Part("latitude") latitude: RequestBody?,
        @Part("longitude") longitude: RequestBody?,
    ): AnexoResposta

    /** Tira uma foto já enviada — o arquivo vai para a lixeira do Drive. */
    @DELETE("ordens/{id}/anexos/{anexoId}")
    suspend fun removerAnexo(@Path("id") id: String, @Path("anexoId") anexoId: String): OrdemTalvezResposta

    /** A foto de um anexo, para mostrar a que não foi tirada neste celular. */
    @Streaming
    @GET("ordens/{id}/anexos/{anexoId}")
    suspend fun baixarAnexo(@Path("id") id: String, @Path("anexoId") anexoId: String): ResponseBody

    @POST("ordens/{id}/notas")
    suspend fun anotar(@Path("id") id: String, @Body pedido: NotaPedido): NotaResposta

    // Rastreamento em campo.

    @POST("posicoes")
    suspend fun enviarPosicoes(@Body pedido: PosicoesPedido): PosicoesResposta

    @GET("acompanhamento")
    suspend fun acompanhamento(): AcompanhamentoResposta

    @GET("ordens/{id}/trilha")
    suspend fun trilha(@Path("id") id: String): TrilhaDto

    @GET("equipe")
    suspend fun equipe(): EquipeResposta

    @POST("equipe/{id}/aprovar")
    suspend fun aprovar(@Path("id") id: String, @Body pedido: AprovarPedido): Response<Unit>

    @POST("equipe/{id}/recusar")
    suspend fun recusar(@Path("id") id: String): Response<Unit>

    @PATCH("equipe/{id}")
    suspend fun alterarMembro(@Path("id") id: String, @Body pedido: AlterarMembroPedido): Response<Unit>

    @POST("equipe/{id}/senha")
    suspend fun redefinirSenha(@Path("id") id: String): SenhaProvisoriaResposta
}
