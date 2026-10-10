package br.com.selebi.app.nucleo.rede

import br.com.selebi.app.nucleo.plataforma.Ambiente
import br.com.selebi.app.nucleo.plataforma.nomeDoSistema
import br.com.selebi.app.nucleo.plataforma.versaoDoSistema
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json

/**
 * O [SelebiApi] de verdade, com o Ktor: OkHttp por baixo no Android e o
 * `NSURLSession` no iPhone.
 *
 * Põe o token em toda requisição e percebe quando a sessão acabou. Só um 401
 * **com token** manda para o login — é a resposta do servidor dizendo "essa
 * sessão não vale mais" (desativado, senha redefinida, 60 dias sem uso). Falta
 * de sinal, 500, timeout: nada disso desloga ninguém. O SeeNet aprendeu a
 * diferença depois de muito técnico reclamar que "pede login toda hora".
 */
class SelebiApiKtor(
    private val base: String,
    private val portador: PortadorToken,
    private val eventos: EventosSessao,
    private val json: Json,
) : SelebiApi {

    private val agente = "Selebi-$nomeDoSistema/${Ambiente.versao} ($nomeDoSistema ${versaoDoSistema()})"

    private val autenticacao = createClientPlugin("Autenticacao") {
        onRequest { pedido, _ ->
            pedido.headers[HttpHeaders.UserAgent] = agente
            portador.token?.let { pedido.headers[HttpHeaders.Authorization] = "Bearer $it" }
        }
        onResponse { resposta ->
            val comToken = resposta.call.request.headers[HttpHeaders.Authorization] != null
            val ehLogin = resposta.call.request.url.encodedPath.endsWith("/sessao")
            if (resposta.status.value == 401 && comToken && !ehLogin) eventos.avisarExpirou()
        }
    }

    private val cliente: HttpClient = clienteHttp {
        // Os erros do servidor são lidos aqui, com a mensagem dele.
        expectSuccess = false
        install(HttpTimeout) {
            // Tempos pensados para 3G de povoado: conectar tem que ser rápido
            // ou falhar logo; ler pode demorar um pouco mais.
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 30_000
        }
        install(autenticacao)
    }

    override suspend fun resolverEmpresa(pedido: CodigoEmpresaPedido): EmpresaDto = postar("empresa", pedido)

    override suspend fun cadastrar(pedido: CadastroPedido): CadastroResposta = postar("cadastro", pedido)

    override suspend fun entrar(pedido: LoginPedido): LoginResposta = postar("sessao", pedido)

    override suspend fun sair() = exigir(cliente.delete(base + "sessao"))

    override suspend fun eu(): EuResposta = buscar("eu")

    override suspend fun trocarSenha(pedido: TrocarSenhaPedido): EuResposta = postar("eu/senha", pedido)

    override suspend fun excluirConta(pedido: ExcluirContaPedido): ExclusaoResposta = postar("eu/excluir", pedido)

    override suspend fun ordens(): OrdensResposta = buscar("ordens")

    override suspend fun ordem(id: String): OrdemResposta = buscar("ordens/$id")

    override suspend fun acao(id: String, pedido: AcaoPedido): AcaoResposta = postar("ordens/$id/acoes", pedido)

    override suspend fun responder(id: String, itemId: String, pedido: RespostaPedido): RespostaItemResposta =
        ler(cliente.put(base + "ordens/$id/itens/$itemId") { corpo(pedido) })

    override suspend fun anexar(
        id: String,
        arquivo: ArquivoEnvio,
        idCliente: String,
        categoria: String,
        itemId: String?,
        capturadoEm: String?,
        latitude: String?,
        longitude: String?,
    ): AnexoResposta {
        val bytes = SystemFileSystem.source(Path(arquivo.caminho)).buffered().use { it.readByteArray() }
        val resposta = cliente.submitFormWithBinaryData(
            url = base + "ordens/$id/anexos",
            formData = formData {
                append(
                    "arquivo",
                    bytes,
                    Headers.build {
                        append(HttpHeaders.ContentType, arquivo.tipo)
                        append(HttpHeaders.ContentDisposition, "filename=\"${arquivo.nome}\"")
                    },
                )
                append("idCliente", idCliente)
                append("categoria", categoria)
                itemId?.let { append("itemId", it) }
                capturadoEm?.let { append("capturadoEm", it) }
                latitude?.let { append("latitude", it) }
                longitude?.let { append("longitude", it) }
            },
        )
        return ler(resposta)
    }

    override suspend fun removerAnexo(id: String, anexoId: String): OrdemTalvezResposta =
        ler(cliente.delete(base + "ordens/$id/anexos/$anexoId"))

    override suspend fun baixarAnexo(id: String, anexoId: String): ByteArray {
        val resposta = cliente.get(base + "ordens/$id/anexos/$anexoId")
        exigir(resposta)
        return resposta.body()
    }

    override suspend fun anotar(id: String, pedido: NotaPedido): NotaResposta = postar("ordens/$id/notas", pedido)

    override suspend fun enviarPosicoes(pedido: PosicoesPedido): PosicoesResposta = postar("posicoes", pedido)

    override suspend fun acompanhamento(): AcompanhamentoResposta = buscar("acompanhamento")

    override fun acompanhamentoAoVivo(): Flow<EventoSse> = channelFlow {
        cliente.prepareGet(base + "acompanhamento/stream") {
            header(HttpHeaders.Accept, "text/event-stream")
            // O fluxo fica aberto: o servidor manda um ping a cada 25 s, então
            // 70 s sem nada é conexão morta.
            timeout { socketTimeoutMillis = 70_000 }
        }.execute { resposta ->
            exigir(resposta)
            val canal = resposta.bodyAsChannel()
            var tipo: String? = null
            val dados = StringBuilder()
            while (true) {
                val linha = canal.readUTF8Line() ?: break
                when {
                    linha.isEmpty() -> {
                        if (tipo != null || dados.isNotEmpty()) send(EventoSse(tipo, dados.toString()))
                        tipo = null
                        dados.clear()
                    }
                    linha.startsWith(":") -> Unit // comentário: o ping
                    linha.startsWith("event:") -> tipo = linha.removePrefix("event:").trim()
                    linha.startsWith("data:") -> {
                        if (dados.isNotEmpty()) dados.append('\n')
                        dados.append(linha.removePrefix("data:").removePrefix(" "))
                    }
                }
            }
        }
    }

    override suspend fun trilha(id: String): TrilhaDto = buscar("ordens/$id/trilha")

    override suspend fun equipe(): EquipeResposta = buscar("equipe")

    override suspend fun aprovar(id: String, pedido: AprovarPedido) =
        exigir(cliente.post(base + "equipe/$id/aprovar") { corpo(pedido) })

    override suspend fun recusar(id: String) = exigir(cliente.post(base + "equipe/$id/recusar"))

    override suspend fun alterarMembro(id: String, pedido: AlterarMembroPedido) =
        exigir(cliente.patch(base + "equipe/$id") { corpo(pedido) })

    override suspend fun redefinirSenha(id: String): SenhaProvisoriaResposta =
        ler(cliente.post(base + "equipe/$id/senha"))

    // -------------------------------------------------------------------------

    private suspend inline fun <reified T> buscar(caminho: String): T = ler(cliente.get(base + caminho))

    private suspend inline fun <reified P, reified T> postar(caminho: String, pedido: P): T =
        ler(cliente.post(base + caminho) { corpo(pedido) })

    private inline fun <reified P> HttpRequestBuilder.corpo(pedido: P) {
        setBody(TextContent(json.encodeToString(pedido), ContentType.Application.Json))
    }

    private suspend inline fun <reified T> ler(resposta: HttpResponse): T {
        val texto = resposta.bodyAsText()
        if (!resposta.status.isSuccess()) throw ErroHttp(resposta.status.value, texto)
        return json.decodeFromString(texto)
    }

    private suspend fun exigir(resposta: HttpResponse) {
        if (!resposta.status.isSuccess()) {
            throw ErroHttp(resposta.status.value, runCatching { resposta.bodyAsText() }.getOrNull())
        }
    }
}

/** O cliente HTTP do sistema: OkHttp no Android, `NSURLSession` no iPhone. */
internal expect fun clienteHttp(configurar: HttpClientConfig<*>.() -> Unit): HttpClient
