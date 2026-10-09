package br.com.selebi.app.nucleo.dados

import br.com.selebi.app.nucleo.rede.AcaoPedido
import br.com.selebi.app.nucleo.rede.AcaoResposta
import br.com.selebi.app.nucleo.rede.AcompanhamentoResposta
import br.com.selebi.app.nucleo.rede.AlterarMembroPedido
import br.com.selebi.app.nucleo.rede.AnexoResposta
import br.com.selebi.app.nucleo.rede.AprovarPedido
import br.com.selebi.app.nucleo.rede.CadastroPedido
import br.com.selebi.app.nucleo.rede.CadastroResposta
import br.com.selebi.app.nucleo.rede.CodigoEmpresaPedido
import br.com.selebi.app.nucleo.rede.EmpresaDto
import br.com.selebi.app.nucleo.rede.EquipeResposta
import br.com.selebi.app.nucleo.rede.EuResposta
import br.com.selebi.app.nucleo.rede.LoginPedido
import br.com.selebi.app.nucleo.rede.LoginResposta
import br.com.selebi.app.nucleo.rede.NotaPedido
import br.com.selebi.app.nucleo.rede.NotaResposta
import br.com.selebi.app.nucleo.rede.OrdemResposta
import br.com.selebi.app.nucleo.rede.OrdemTalvezResposta
import br.com.selebi.app.nucleo.rede.OrdensResposta
import br.com.selebi.app.nucleo.rede.PosicoesPedido
import br.com.selebi.app.nucleo.rede.PosicoesResposta
import br.com.selebi.app.nucleo.rede.RespostaItemResposta
import br.com.selebi.app.nucleo.rede.RespostaPedido
import br.com.selebi.app.nucleo.rede.SelebiApi
import br.com.selebi.app.nucleo.rede.SenhaProvisoriaResposta
import br.com.selebi.app.nucleo.rede.TrilhaDto
import br.com.selebi.app.nucleo.rede.TrocarSenhaPedido
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response

/** O servidor de mentira: cada teste diz como ele responde. */
internal class ApiFalsa : SelebiApi {
    val chamadas = mutableListOf<String>()
    var ultimoIdClienteDeFoto: String? = null
    var aoAgir: (String, AcaoPedido) -> AcaoResposta = { _, _ -> error("ação não esperada") }
    var aoResponder: (String, RespostaPedido) -> RespostaItemResposta = { _, _ -> error("resposta não esperada") }
    var aoAnexar: (String, String) -> AnexoResposta = { _, _ -> error("foto não esperada") }
    var aoBuscar: (String) -> OrdemResposta = { error("busca não esperada") }

    override suspend fun acao(id: String, pedido: AcaoPedido): AcaoResposta {
        chamadas += "acao:${pedido.acao}"
        return aoAgir(id, pedido)
    }

    override suspend fun responder(id: String, itemId: String, pedido: RespostaPedido): RespostaItemResposta {
        chamadas += "resposta:$itemId"
        return aoResponder(itemId, pedido)
    }

    override suspend fun anexar(
        id: String,
        arquivo: MultipartBody.Part,
        idCliente: RequestBody,
        categoria: RequestBody,
        itemId: RequestBody?,
        capturadoEm: RequestBody?,
        latitude: RequestBody?,
        longitude: RequestBody?,
    ): AnexoResposta {
        chamadas += "anexo"
        val buffer = okio.Buffer()
        idCliente.writeTo(buffer)
        ultimoIdClienteDeFoto = buffer.readUtf8()
        return aoAnexar(id, ultimoIdClienteDeFoto!!)
    }

    override suspend fun ordem(id: String): OrdemResposta = aoBuscar(id)

    var aoEnviarPosicoes: (PosicoesPedido) -> PosicoesResposta = { error("posições não esperadas") }
    val lotesDePosicoes = mutableListOf<PosicoesPedido>()

    override suspend fun enviarPosicoes(pedido: PosicoesPedido): PosicoesResposta {
        chamadas += "posicoes:${pedido.pontos.size}"
        lotesDePosicoes += pedido
        return aoEnviarPosicoes(pedido)
    }

    override suspend fun acompanhamento(): AcompanhamentoResposta = error("")
    override suspend fun trilha(id: String): TrilhaDto = error("")

    override suspend fun anotar(id: String, pedido: NotaPedido): NotaResposta = error("nota não esperada")
    override suspend fun resolverEmpresa(pedido: CodigoEmpresaPedido): EmpresaDto = error("")
    override suspend fun cadastrar(pedido: CadastroPedido): CadastroResposta = error("")
    override suspend fun entrar(pedido: LoginPedido): LoginResposta = error("")
    override suspend fun sair(): Response<Unit> = error("")
    override suspend fun eu(): EuResposta = error("")
    override suspend fun trocarSenha(pedido: TrocarSenhaPedido): EuResposta = error("")
    override suspend fun ordens(): OrdensResposta = error("")
    override suspend fun removerAnexo(id: String, anexoId: String): OrdemTalvezResposta = error("")
    override suspend fun baixarAnexo(id: String, anexoId: String): ResponseBody = error("")
    override suspend fun equipe(): EquipeResposta = error("")
    override suspend fun aprovar(id: String, pedido: AprovarPedido): Response<Unit> = error("")
    override suspend fun recusar(id: String): Response<Unit> = error("")
    override suspend fun alterarMembro(id: String, pedido: AlterarMembroPedido): Response<Unit> = error("")
    override suspend fun redefinirSenha(id: String): SenhaProvisoriaResposta = error("")
}
