package br.com.selebi.app.nucleo.dados

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * O tempo entre as tentativas de envio do iPhone, no relógio virtual do teste.
 *
 * O defeito que isto cobre: com a internet ligada e o servidor dando erro, o app
 * tentava a cada 2 segundos, sem parar (25 pedidos seguidos no servidor).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LacoDeEnvioTest {

    private class Montagem(val teste: TestScope, rede: Boolean = true, val resultado: () -> ResultadoEnvio) {
        val rede = MutableStateFlow(rede)

        /** O instante (em segundos, no relógio do teste) de cada tentativa. */
        val tentativas = mutableListOf<Long>()
        val laco = LacoDeEnvio(
            escopo = teste.backgroundScope,
            rede = this.rede,
            envioUmaVez = {
                tentativas += teste.currentTime / 1000
                resultado()
            },
        ).also { it.iniciar() }
    }

    private suspend fun TestScope.passar(segundos: Long) {
        advanceTimeBy(segundos * 1000)
        runCurrent()
    }

    @Test
    fun `erro do servidor com internet ligada espera cada vez mais entre as tentativas`() = runTest {
        val m = Montagem(this) { ResultadoEnvio.TENTAR_DE_NOVO }
        m.laco.pedir()
        runCurrent()
        passar(1_000)

        // 0 s, depois 30 s, 60 s, 120 s, 240 s, 480 s de espera: no máximo 6 em ~17 min.
        // Antes da correção eram umas 500.
        assertEquals(listOf(0L, 30L, 90L, 210L, 450L, 930L), m.tentativas)
    }

    @Test
    fun `a espera para de crescer em 15 minutos`() = runTest {
        val m = Montagem(this) { ResultadoEnvio.TENTAR_DE_NOVO }
        m.laco.pedir()
        runCurrent()
        passar(3 * 3600)
        val intervalos = m.tentativas.zipWithNext { a, b -> b - a }
        assertEquals(900L, intervalos.last())
        assertEquals(900L, intervalos.takeLast(5).max())
    }

    @Test
    fun `pedido novo no meio da espera tenta na hora`() = runTest {
        val m = Montagem(this) { ResultadoEnvio.TENTAR_DE_NOVO }
        m.laco.pedir()
        runCurrent()
        val primeira = m.tentativas.size
        passar(10)
        assertEquals(primeira, m.tentativas.size) // ainda esperando os 30 s

        m.laco.pedir() // "Tentar agora", ou o app voltou para a frente
        runCurrent()
        assertEquals(primeira + 1, m.tentativas.size)
    }

    @Test
    fun `rede que volta depois de cair tenta na hora, uma vez so`() = runTest {
        val m = Montagem(this, rede = false) { ResultadoEnvio.TENTAR_DE_NOVO }
        m.laco.pedir()
        runCurrent()
        assertEquals(1, m.tentativas.size)

        passar(5)
        m.rede.value = true
        runCurrent()
        assertEquals("tenta quando a rede volta", 2, m.tentativas.size)

        passar(20) // ainda dentro da nova espera, que agora é de 60 s
        assertEquals("sem segunda tentativa colada na primeira", 2, m.tentativas.size)
    }

    @Test
    fun `envio que dá certo para e espera o proximo pedido`() = runTest {
        val m = Montagem(this) { ResultadoEnvio.TUDO_ENVIADO }
        m.laco.pedir()
        runCurrent()
        passar(3600)
        assertEquals(1, m.tentativas.size)

        m.laco.pedir()
        runCurrent()
        assertEquals(2, m.tentativas.size)
    }

    @Test
    fun `sem sessao nao fica tentando`() = runTest {
        val m = Montagem(this) { ResultadoEnvio.SEM_SESSAO }
        m.laco.pedir()
        runCurrent()
        passar(3600)
        assertEquals(1, m.tentativas.size)
    }
}
