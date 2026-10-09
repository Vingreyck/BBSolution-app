package br.com.selebi.app.nucleo.rastreio

import android.Manifest
import androidx.room.Room
import br.com.selebi.app.amostras.Amostras
import br.com.selebi.app.amostras.AmostrasExecucao
import br.com.selebi.app.nucleo.banco.PosicaoEntidade
import br.com.selebi.app.nucleo.banco.SelebiBanco
import br.com.selebi.app.nucleo.dados.ApiFalsa
import br.com.selebi.app.nucleo.dados.Localizacao
import br.com.selebi.app.nucleo.dados.ResultadoEnvio
import br.com.selebi.app.nucleo.modelo.ModoRastreio
import br.com.selebi.app.nucleo.modelo.StatusOs
import br.com.selebi.app.nucleo.rede.ExecutorApi
import br.com.selebi.app.nucleo.rede.ModuloRede
import br.com.selebi.app.nucleo.rede.PosicoesResposta
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * O rastreamento no celular: quando liga, em que modo, quando desliga — e as
 * posições subindo em lote, esperando o sinal e nunca em nome de outra pessoa.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RastreioTest {
    private val contexto = RuntimeEnvironment.getApplication()
    private lateinit var banco: SelebiBanco
    private lateinit var envio: EnvioPosicoes
    private lateinit var rastreador: Rastreador
    private lateinit var memoria: MemoriaRastreio
    private val api = ApiFalsa()

    @Before
    fun montar() {
        banco = Room.inMemoryDatabaseBuilder(contexto, SelebiBanco::class.java).allowMainThreadQueries().build()
        envio = EnvioPosicoes(banco.posicoes(), api, ExecutorApi(ModuloRede.json()))
        memoria = MemoriaRastreio(contexto)
        memoria.apagar()
        rastreador = Rastreador(contexto, memoria, Localizacao(contexto))
    }

    @After
    fun desmontar() = banco.close()

    private fun permitir() = shadowOf(contexto).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun servicoPedido(): String? = shadowOf(contexto).nextStartedService?.action

    // --- quando liga e desliga ------------------------------------------------

    @Test
    fun `sem permissao de localizacao nao liga nada`() {
        val ordem = AmostrasExecucao.vistoriaEmCampo().copy(status = StatusOs.EM_DESLOCAMENTO)
        rastreador.conferir(listOf(ordem), Amostras.tecnico)
        assertNull(memoria.ler())
        assertNull(servicoPedido())
    }

    @Test
    fun `a caminho liga no modo forte, atendendo passa para o economico`() {
        permitir()
        val caminho = AmostrasExecucao.vistoriaEmCampo().copy(status = StatusOs.EM_DESLOCAMENTO)
        rastreador.conferir(listOf(caminho), Amostras.tecnico)
        assertEquals(ModoRastreio.DESLOCAMENTO, memoria.ler()?.modo)
        assertEquals(ServicoRastreamento.ACAO_INICIAR, servicoPedido())

        rastreador.conferir(listOf(caminho.copy(status = StatusOs.EM_ANDAMENTO)), Amostras.tecnico)
        assertEquals(ModoRastreio.ECO, memoria.ler()?.modo)
        assertEquals(caminho.id, rastreador.ativo.value?.ordemId)
    }

    @Test
    fun `OS que saiu de campo desliga`() {
        permitir()
        val ordem = AmostrasExecucao.vistoriaEmCampo()
        rastreador.conferir(listOf(ordem), Amostras.tecnico)
        servicoPedido()
        rastreador.conferir(listOf(ordem.copy(status = StatusOs.AGUARDANDO_PECA)), Amostras.tecnico)
        assertNull(memoria.ler())
        assertEquals(ServicoRastreamento.ACAO_PARAR, servicoPedido())
    }

    @Test
    fun `OS de outra pessoa nao rastreia este celular`() {
        permitir()
        val doRafael = AmostrasExecucao.vistoriaEmCampo().copy(responsavel = br.com.selebi.app.nucleo.modelo.Pessoa("u3", "Rafael"))
        rastreador.conferir(listOf(doRafael), Amostras.tecnico)
        assertNull(memoria.ler())
    }

    @Test
    fun `a caminho tem preferencia sobre a que esta atendendo`() {
        permitir()
        val atendendo = AmostrasExecucao.vistoriaEmCampo()
        val caminho = AmostrasExecucao.instalacaoParaConcluir().copy(status = StatusOs.EM_DESLOCAMENTO)
        rastreador.conferir(listOf(atendendo, caminho), Amostras.tecnico)
        assertEquals(caminho.id, memoria.ler()?.ordemId)
    }

    @Test
    fun `sair do app desliga`() {
        permitir()
        rastreador.conferir(listOf(AmostrasExecucao.vistoriaEmCampo()), Amostras.tecnico)
        rastreador.conferir(emptyList(), null)
        assertNull(memoria.ler())
    }

    @Test
    fun `parar so vale para a OS que esta sendo rastreada`() {
        permitir()
        val ordem = AmostrasExecucao.vistoriaEmCampo()
        rastreador.conferir(listOf(ordem), Amostras.tecnico)
        rastreador.parar("outra-os")
        assertEquals(ordem.id, memoria.ler()?.ordemId)
        rastreador.parar(ordem.id)
        assertNull(memoria.ler())
    }

    // --- o envio das posições ---------------------------------------------------

    private fun ponto(i: Int, usuario: String = "u2") = PosicaoEntidade(
        usuarioId = usuario,
        ordemId = "o49",
        latitude = -10.9 + i * 0.0007,
        longitude = -37.05,
        precisao = 8f,
        velocidade = 12f,
        bateria = 70,
        modo = "deslocamento",
        capturadoEm = System.currentTimeMillis() - (500 - i) * 1000L,
    )

    @Test
    fun `posicoes sobem em lotes, das mais velhas para as mais novas, e saem do celular`() = runBlocking {
        repeat(450) { envio.guardar(ponto(it)) }
        api.aoEnviarPosicoes = { PosicoesResposta(recebidos = it.pontos.size, aceitos = it.pontos.size) }
        assertEquals(ResultadoEnvio.TUDO_ENVIADO, envio.enviar("u2"))
        assertEquals(listOf(200, 200, 50), api.lotesDePosicoes.map { it.pontos.size })
        val primeiro = api.lotesDePosicoes.first().pontos
        assertTrue("em ordem de captura", primeiro.zipWithNext().all { (a, b) -> a.capturadoEm <= b.capturadoEm })
        assertEquals(0, banco.posicoes().contar("u2"))
    }

    @Test
    fun `sem sinal as posicoes esperam no celular`() = runBlocking {
        repeat(5) { envio.guardar(ponto(it)) }
        api.aoEnviarPosicoes = { throw IOException("sem rede") }
        assertEquals(ResultadoEnvio.TENTAR_DE_NOVO, envio.enviar("u2"))
        assertEquals(5, banco.posicoes().contar("u2"))
    }

    @Test
    fun `sessao vencida guarda as posicoes para depois`() = runBlocking {
        repeat(3) { envio.guardar(ponto(it)) }
        api.aoEnviarPosicoes = {
            throw HttpException(Response.error<Any>(401, """{"erro":"nao_autenticado","mensagem":"Entre de novo."}""".toResponseBody("application/json".toMediaType())))
        }
        assertEquals(ResultadoEnvio.SEM_SESSAO, envio.enviar("u2"))
        assertEquals(3, banco.posicoes().contar("u2"))
    }

    @Test
    fun `lote recusado e descartado para nao travar os seguintes`() = runBlocking {
        repeat(3) { envio.guardar(ponto(it)) }
        api.aoEnviarPosicoes = {
            throw HttpException(Response.error<Any>(400, """{"erro":"validacao","mensagem":"x"}""".toResponseBody("application/json".toMediaType())))
        }
        assertEquals(ResultadoEnvio.TUDO_ENVIADO, envio.enviar("u2"))
        assertEquals(0, banco.posicoes().contar("u2"))
    }

    @Test
    fun `as posicoes de uma pessoa nao sobem em nome de outra`() = runBlocking {
        envio.guardar(ponto(1, usuario = "u2"))
        envio.guardar(ponto(2, usuario = "u9"))
        api.aoEnviarPosicoes = { PosicoesResposta(aceitos = it.pontos.size) }
        envio.enviar("u2")
        assertEquals(listOf(1), api.lotesDePosicoes.map { it.pontos.size })
        envio.descartarDeOutros("u2")
        assertEquals(0, banco.posicoes().contar("u9"))
    }
}
