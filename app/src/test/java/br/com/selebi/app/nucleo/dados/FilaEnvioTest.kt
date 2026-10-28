package br.com.selebi.app.nucleo.dados

import androidx.room.Room
import br.com.selebi.app.nucleo.arquivos.ArquivosOs
import br.com.selebi.app.nucleo.arquivos.PastasApp
import br.com.selebi.app.nucleo.arquivos.ProcessadorFotosAndroid
import br.com.selebi.app.nucleo.rede.ErroHttp
import br.com.selebi.app.nucleo.banco.AnexoEntidade
import br.com.selebi.app.nucleo.banco.PendenteEntidade
import br.com.selebi.app.nucleo.banco.SelebiBanco
import br.com.selebi.app.nucleo.banco.paraModelo
import br.com.selebi.app.nucleo.modelo.EsperaEnvio
import br.com.selebi.app.nucleo.modelo.MotivoEspera
import br.com.selebi.app.nucleo.modelo.Ordem
import br.com.selebi.app.nucleo.modelo.Resposta
import br.com.selebi.app.nucleo.modelo.StatusOs
import br.com.selebi.app.nucleo.rede.AcaoPedido
import br.com.selebi.app.nucleo.rede.AcaoResposta
import br.com.selebi.app.nucleo.rede.AlterarMembroPedido
import br.com.selebi.app.nucleo.rede.AnexoCriadoDto
import br.com.selebi.app.nucleo.rede.AnexoDto
import br.com.selebi.app.nucleo.rede.AnexoResposta
import br.com.selebi.app.nucleo.rede.AprovarPedido
import br.com.selebi.app.nucleo.rede.CadastroPedido
import br.com.selebi.app.nucleo.rede.CadastroResposta
import br.com.selebi.app.nucleo.rede.ClienteDto
import br.com.selebi.app.nucleo.rede.CodigoEmpresaPedido
import br.com.selebi.app.nucleo.rede.EmpresaDto
import br.com.selebi.app.nucleo.rede.EquipeResposta
import br.com.selebi.app.nucleo.rede.EuResposta
import br.com.selebi.app.nucleo.rede.ExecutorApi
import br.com.selebi.app.nucleo.rede.ItemChecklistDto
import br.com.selebi.app.nucleo.rede.LoginPedido
import br.com.selebi.app.nucleo.rede.LoginResposta
import br.com.selebi.app.nucleo.rede.ModuloRede
import br.com.selebi.app.nucleo.rede.NotaDto
import br.com.selebi.app.nucleo.rede.NotaPedido
import br.com.selebi.app.nucleo.rede.NotaResposta
import br.com.selebi.app.nucleo.rede.OrdemDto
import br.com.selebi.app.nucleo.rede.OrdemResposta
import br.com.selebi.app.nucleo.rede.OrdemTalvezResposta
import br.com.selebi.app.nucleo.rede.OrdensResposta
import br.com.selebi.app.nucleo.rede.RespostaItemResposta
import br.com.selebi.app.nucleo.rede.RespostaPedido
import br.com.selebi.app.nucleo.rede.SelebiApi
import br.com.selebi.app.nucleo.rede.SenhaProvisoriaResposta
import br.com.selebi.app.nucleo.rede.TrocarSenhaPedido
import br.com.selebi.app.nucleo.rede.paraJson
import br.com.selebi.app.nucleo.util.Datas
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import kotlinx.datetime.Instant
import java.util.UUID

/**
 * A fila offline de ponta a ponta: banco de verdade (em memória) e um
 * servidor de mentira que aceita, recusa ou "perde o sinal".
 *
 * É aqui que se garante o que o técnico no telhado precisa: o que ele fez
 * vale na tela na hora, sobrevive à sincronização, sobe na ordem, e a recusa
 * do servidor não trava o resto.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FilaEnvioTest {

    private lateinit var banco: SelebiBanco
    private lateinit var copia: CopiaLocal
    private lateinit var fila: FilaEnvio
    private lateinit var arquivos: ArquivosOs
    private val api = ApiFalsa()
    private val json = ModuloRede.json()
    private var relogio = 1_000L
    private var agendamentos = 0

    @Before
    fun montar() {
        val contexto = RuntimeEnvironment.getApplication()
        banco = Room.inMemoryDatabaseBuilder(contexto, SelebiBanco::class.java).allowMainThreadQueries().build()
        arquivos = ArquivosOs(PastasApp(contexto.filesDir.path, contexto.cacheDir.path), ProcessadorFotosAndroid(contexto))
        copia = CopiaLocal(banco, banco.ordens(), banco.pendentes(), arquivos, json)
        fila = FilaEnvio(
            banco, banco.pendentes(), banco.ordens(), banco.avisos(), copia, api, ExecutorApi(json), arquivos, json,
        ) { agendamentos++ }
    }

    @After
    fun desmontar() = banco.close()

    // --- montagem ---------------------------------------------------------------

    private fun dto(
        versao: String = "2026-09-25T12:00:00.000Z",
        status: String = "em_andamento",
        disjuntor: JsonElement? = null,
        disjuntorOk: Boolean = false,
        parede: JsonElement? = null,
        anexos: List<AnexoDto> = emptyList(),
        notas: List<NotaDto> = emptyList(),
        itens: List<ItemChecklistDto>? = null,
    ) = OrdemDto(
        id = "os1",
        numero = 1,
        tipo = "vistoria",
        status = status,
        descricao = "Vistoria de teste",
        criadoEm = "2026-09-20T10:00:00.000Z",
        cliente = ClienteDto("c1", "Cliente Teste"),
        checklist = itens ?: listOf(
            ItemChecklistDto("i1", 1, "Disjuntor — amperagem", obrigatorio = true, concluido = disjuntorOk, tipoResposta = "numero", fotosMinimas = 1, unidade = "A", valor = disjuntor),
            ItemChecklistDto("i2", 2, "Vai precisar rasgar parede?", obrigatorio = true, concluido = parede != null, tipoResposta = "sim_nao", valor = parede),
        ),
        anexos = anexos,
        notas = notas,
        atualizadoEm = versao,
    )

    private fun resposta(
        itemId: String,
        valor: Resposta?,
        usuario: String = "u1",
        observacao: String? = null,
        agendar: Boolean? = null,
    ) = PendenteEntidade(
        idCliente = UUID.randomUUID().toString(),
        ordemId = "os1",
        usuarioId = usuario,
        tipo = TipoPendente.RESPOSTA,
        corpoJson = json.encodeToString(
            RespostaPedido.serializer(),
            RespostaPedido(valor.paraJson(), observacao, Datas.agoraMesmo().toString(), agendar),
        ),
        itemId = itemId,
        criadoEm = relogio++,
    )

    private fun acao(nome: String, usuario: String = "u1") = PendenteEntidade(
        idCliente = UUID.randomUUID().toString(),
        ordemId = "os1",
        usuarioId = usuario,
        tipo = TipoPendente.ACAO,
        corpoJson = json.encodeToString(AcaoPedido.serializer(), AcaoPedido(UUID.randomUUID().toString(), nome, motivo = "Aguardando peça")),
        criadoEm = relogio++,
    )

    private fun foto(itemId: String): PendenteEntidade {
        val id = UUID.randomUUID().toString()
        val arquivo = File(arquivos.pastaDa("os1"), "$id.jpg").apply { writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1, 2, 3)) }
        return PendenteEntidade(
            idCliente = id,
            ordemId = "os1",
            usuarioId = "u1",
            tipo = TipoPendente.ANEXO,
            corpoJson = json.encodeToString(AnexoNaFila.serializer(), AnexoNaFila("foto", itemId, Datas.agoraMesmo().toString())),
            itemId = itemId,
            arquivo = arquivo.path,
            criadoEm = relogio++,
        )
    }

    private suspend fun ordem(): Ordem = banco.ordens().buscar("os1")!!.paraModelo()

    private fun recusa(status: Int, erro: String, mensagem: String): ErroHttp =
        ErroHttp(status, """{"erro":"$erro","mensagem":"$mensagem"}""")

    // --- o que vale na tela -------------------------------------------------------

    @Test
    fun `resposta sem sinal vale na hora e sobrevive a sincronizacao`() = runBlocking {
        copia.gravarTodas(listOf(dto()))
        fila.enfileirar(resposta("i2", Resposta.Marcado(false)))
        assertTrue(ordem().checklist.first { it.id == "i2" }.concluido)
        assertEquals(1, agendamentos)

        // A lista chega de novo, ainda sem a resposta (ela não subiu): o item continua verde.
        copia.gravarTodas(listOf(dto()))
        val item = ordem().checklist.first { it.id == "i2" }
        assertTrue(item.concluido)
        assertEquals(Resposta.Marcado(false), item.valor)
    }

    @Test
    fun `foto que espera sinal conta para o item e nao some na sincronizacao`() = runBlocking {
        copia.gravarTodas(listOf(dto()))
        fila.enfileirar(resposta("i1", Resposta.Numero(40.0)))
        assertFalse("sem a foto, o disjuntor não está cumprido", ordem().checklist.first().concluido)

        fila.enfileirar(foto("i1"))
        assertTrue(ordem().checklist.first().concluido)

        copia.gravarTodas(listOf(dto()))
        val depois = ordem()
        assertTrue(depois.checklist.first().concluido)
        assertEquals(1, depois.fotosDo("i1").size)
        assertTrue(depois.fotosDo("i1").single().pendente)
    }

    @Test
    fun `resposta nova do mesmo item toma o lugar da que nao subiu`() = runBlocking {
        copia.gravarTodas(listOf(dto()))
        fila.enfileirar(resposta("i1", Resposta.Numero(40.0)))
        fila.enfileirar(resposta("i1", Resposta.Numero(50.0)))
        val fila = banco.pendentes().todos()
        assertEquals(1, fila.size)
        assertEquals(Resposta.Numero(50.0), ordem().checklist.first().valor)
    }

    @Test
    fun `lista velha nao passa por cima da OS nova`() = runBlocking {
        copia.gravarUma(dto(versao = "2026-09-25T12:05:00.000Z", parede = JsonPrimitive(true)))
        // A lista pedida antes chega depois, com a OS de 12:00.
        copia.gravarTodas(listOf(dto(versao = "2026-09-25T12:00:00.000Z")))
        assertEquals(Resposta.Marcado(true), ordem().checklist.first { it.id == "i2" }.valor)
    }

    @Test
    fun `nota escrita sem sinal aparece como pendente e sobrevive a sincronizacao`() = runBlocking {
        copia.gravarTodas(listOf(dto(notas = listOf(NotaDto("n1", "Portão pelo lado da padaria.", "u9", "Paula", "2026-09-24T10:00:00.000Z")))))
        fila.enfileirar(
            PendenteEntidade(
                idCliente = "nota-1", ordemId = "os1", usuarioId = "u1", tipo = TipoPendente.NOTA,
                corpoJson = json.encodeToString(NotaPedido.serializer(), NotaPedido("nota-1", "Chegou só um inversor.", "2026-09-25T13:00:00Z")),
                criadoEm = relogio++,
            ),
        )
        copia.gravarTodas(listOf(dto(notas = listOf(NotaDto("n1", "Portão pelo lado da padaria.", "u9", "Paula", "2026-09-24T10:00:00.000Z")))))
        val notas = ordem().notas
        assertEquals(listOf("Portão pelo lado da padaria.", "Chegou só um inversor."), notas.map { it.texto })
        assertTrue(notas.last().pendente)
    }

    // --- o envio ------------------------------------------------------------------

    @Test
    fun `envio aceito sai da fila e a OS passa a ser a do servidor`() = runBlocking {
        copia.gravarTodas(listOf(dto()))
        fila.enfileirar(resposta("i2", Resposta.Marcado(true)))
        api.aoResponder = { _, _ -> RespostaItemResposta(dto(versao = "2026-09-25T12:01:00.000Z", parede = JsonPrimitive(true)), avisos = listOf("Anotado.")) }

        assertEquals(ResultadoEnvio.TUDO_ENVIADO, fila.enviar("u1"))
        assertTrue(banco.pendentes().todos().isEmpty())
        assertTrue(ordem().checklist.first { it.id == "i2" }.concluido)
    }

    @Test
    fun `sem sinal para no primeiro e tenta depois, sem mandar fora de ordem`() = runBlocking {
        copia.gravarTodas(listOf(dto()))
        fila.enfileirar(resposta("i1", Resposta.Numero(40.0)))
        fila.enfileirar(resposta("i2", Resposta.Marcado(true)))
        api.aoResponder = { _, _ -> throw IOException("sem rede") }

        assertEquals(ResultadoEnvio.TENTAR_DE_NOVO, fila.enviar("u1"))
        assertEquals(1, api.chamadas.size)
        val pendentes = banco.pendentes().todos()
        assertEquals(2, pendentes.size)
        assertEquals(1, pendentes.first().tentativas)
        assertNull(pendentes.first().erro)
    }

    @Test
    fun `recusa do servidor fica marcada, a tela volta a ser a dele e o resto sobe`() = runBlocking {
        copia.gravarTodas(listOf(dto()))
        fila.enfileirar(acao("pausar"))
        assertEquals(StatusOs.AGUARDANDO_PECA, ordem().status)
        fila.enfileirar(resposta("i2", Resposta.Marcado(true)))

        api.aoAgir = { _, _ -> throw recusa(409, "transicao_invalida", "Não dá para pausar uma OS que está \\\"Concluída\\\".") }
        api.aoBuscar = { OrdemResposta(dto(versao = "2026-09-25T12:02:00.000Z", status = "concluida")) }
        api.aoResponder = { _, _ -> throw recusa(409, "os_encerrada", "A OS já foi encerrada; o checklist não muda mais.") }

        assertEquals(ResultadoEnvio.TUDO_ENVIADO, fila.enviar("u1"))
        val pendentes = banco.pendentes().todos()
        assertEquals(2, pendentes.size)
        assertTrue(pendentes.all { it.erro != null })
        assertTrue(pendentes.first().erro!!.startsWith("Não dá para pausar"))
        // A tela mostra a OS como o servidor tem: concluída, sem a resposta recusada.
        val agora = ordem()
        assertEquals(StatusOs.CONCLUIDA, agora.status)
        assertNull(agora.checklist.first { it.id == "i2" }.valor)

        // O técnico viu e descartou.
        pendentes.forEach { fila.descartar(it.idCliente) }
        assertTrue(banco.pendentes().todos().isEmpty())
    }

    @Test
    fun `a fila diz por que esta esperando e limpa quando sobe`() = runBlocking {
        copia.gravarTodas(listOf(dto()))
        fila.enfileirar(resposta("i2", Resposta.Marcado(true)))
        assertNull(fila.espera.value)

        api.aoResponder = { _, _ -> throw IOException("sem rede") }
        assertEquals(ResultadoEnvio.TENTAR_DE_NOVO, fila.enviar("u1"))
        assertEquals(EsperaEnvio(MotivoEspera.SEM_SINAL), fila.espera.value)

        // Com internet ligada mas o servidor dando erro, a tela precisa dizer isso, com o número.
        api.aoResponder = { _, _ -> throw recusa(500, "interno", "Erro interno.") }
        assertEquals(ResultadoEnvio.TENTAR_DE_NOVO, fila.enviar("u1"))
        assertEquals(EsperaEnvio(MotivoEspera.SERVIDOR, 500), fila.espera.value)

        api.aoResponder = { _, _ -> RespostaItemResposta(dto(versao = "2026-09-25T12:01:00.000Z"), avisos = emptyList()) }
        assertEquals(ResultadoEnvio.TUDO_ENVIADO, fila.enviar("u1"))
        assertNull(fila.espera.value)
    }

    @Test
    fun `sessao vencida limpa o motivo da espera`() = runBlocking {
        copia.gravarTodas(listOf(dto()))
        fila.enfileirar(resposta("i2", Resposta.Marcado(true)))
        api.aoResponder = { _, _ -> throw IOException("sem rede") }
        fila.enviar("u1")
        assertNotNull(fila.espera.value)

        api.aoResponder = { _, _ -> throw recusa(401, "sessao_invalida", "Entre de novo.") }
        assertEquals(ResultadoEnvio.SEM_SESSAO, fila.enviar("u1"))
        assertNull(fila.espera.value)
    }

    @Test
    fun `observacao obrigatoria e agendar valem na tela na hora e sobem no pedido`() = runBlocking {
        val aterramento = ItemChecklistDto(
            "i3", 3, "Aterramento", obrigatorio = true, tipoResposta = "escolha",
            opcoes = listOf("Existe e está bom", "Não existe"), fotosMinimas = 1, exigeObservacao = "sempre",
        )
        val sombra = ItemChecklistDto(
            "i4", 4, "Sombra sobre o telhado", obrigatorio = true, tipoResposta = "escolha",
            opcoes = listOf("Sem sombra", "Muita sombra"), fotosMinimas = 1, exigeObservacao = "sem_foto", permiteAgendar = true,
        )
        copia.gravarTodas(listOf(dto(itens = listOf(aterramento, sombra))))
        // A foto do aterramento já está no celular.
        banco.ordens().salvarAnexos(
            listOf(AnexoEntidade("a1", "os1", "i3", "foto", Datas.agoraMesmo().toString(), pendente = true, enviadoPorId = "u1")),
        )
        suspend fun item(id: String) = ordem().checklist.first { it.id == id }

        // Com a foto, mas sem a observação que o item sempre pede: não está cumprido.
        fila.enfileirar(resposta("i3", Resposta.Texto("Não existe")))
        assertFalse(item("i3").concluido)
        // A observação chega junto: cumprido, e o pedido leva a observação.
        fila.enfileirar(resposta("i3", Resposta.Texto("Não existe"), observacao = "Sem haste no poste."))
        assertTrue(item("i3").concluido)
        assertEquals("Sem haste no poste.", item("i3").observacao)

        // Sem foto, a explicação vale no lugar dela; "agendar" também deixa o item cumprido.
        fila.enfileirar(resposta("i4", null, agendar = true))
        assertTrue(item("i4").concluido)
        assertTrue(item("i4").agendar)

        // Responder o item de verdade tira ele da visita de volta, e sem foto nem explicação não cumpre.
        fila.enfileirar(resposta("i4", Resposta.Texto("Muita sombra"), agendar = false))
        assertFalse(item("i4").agendar)
        assertFalse(item("i4").concluido)

        // O que sobe para o servidor leva o estado inteiro.
        val enviados = mutableMapOf<String, RespostaPedido>()
        api.aoResponder = { itemId, pedido ->
            enviados[itemId] = pedido
            RespostaItemResposta(dto(itens = listOf(aterramento, sombra), versao = "2026-09-25T12:09:00.000Z"))
        }
        assertEquals(ResultadoEnvio.TUDO_ENVIADO, fila.enviar("u1"))
        assertEquals("Sem haste no poste.", enviados.getValue("i3").observacao)
        assertEquals(false, enviados.getValue("i4").agendar)
    }

    @Test
    fun `sessao vencida para a fila sem perder nada`() = runBlocking {
        copia.gravarTodas(listOf(dto()))
        fila.enfileirar(resposta("i2", Resposta.Marcado(true)))
        api.aoResponder = { _, _ -> throw recusa(401, "sessao_invalida", "Entre de novo.") }
        assertEquals(ResultadoEnvio.SEM_SESSAO, fila.enviar("u1"))
        assertEquals(1, banco.pendentes().todos().size)
        assertNull(banco.pendentes().todos().single().erro)
    }

    @Test
    fun `foto aceita ganha o nome do id do servidor e continua aparecendo`() = runBlocking {
        copia.gravarTodas(listOf(dto()))
        val p = foto("i1")
        fila.enfileirar(p)
        api.aoAnexar = { _, _ ->
            AnexoResposta(
                AnexoCriadoDto("srv-1"),
                dto(versao = "2026-09-25T12:03:00.000Z", anexos = listOf(AnexoDto("srv-1", "i1", "foto", "2026-09-25T12:03:00.000Z", "u1"))),
            )
        }
        assertEquals(ResultadoEnvio.TUDO_ENVIADO, fila.enviar("u1"))

        val anexo = ordem().anexos.single()
        assertEquals("srv-1", anexo.id)
        assertFalse(anexo.pendente)
        assertNotNull("a miniatura continua vindo do arquivo local", anexo.arquivoLocal)
        assertTrue(File(anexo.arquivoLocal!!).exists())
        assertFalse(File(p.arquivo!!).exists())
        assertEquals("idCliente vai no formulário", p.idCliente, api.ultimoIdClienteDeFoto)
    }

    @Test
    fun `outra pessoa entra neste celular - a fila da anterior e descartada`() = runBlocking {
        copia.gravarTodas(listOf(dto()))
        fila.enfileirar(resposta("i1", Resposta.Numero(40.0), usuario = "u1"))
        fila.enfileirar(resposta("i2", Resposta.Marcado(true), usuario = "u2"))
        fila.descartarDeOutros("u2")
        assertEquals(listOf("u2"), banco.pendentes().todos().map { it.usuarioId })
    }

    @Test
    fun `a fila de uma pessoa nao sobe em nome de outra`() = runBlocking {
        copia.gravarTodas(listOf(dto()))
        fila.enfileirar(resposta("i2", Resposta.Marcado(true), usuario = "u1"))
        assertEquals(ResultadoEnvio.TUDO_ENVIADO, fila.enviar("u2"))
        assertTrue(api.chamadas.isEmpty())
    }
}
