package br.com.selebi.app.nucleo.modelo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * As regras do rastreamento no app. O frescor tem o gêmeo no servidor
 * (`frescorDa`, em `src/rastreamento/trilha.ts`): o mesmo técnico precisa
 * aparecer "ao vivo" no site e no app ao mesmo tempo.
 */
class CampoTest {
    private val agora = Instant.parse("2026-09-25T13:00:00Z")
    private fun ha(segundos: Long) = agora.minusSeconds(segundos)

    @Test
    fun `frescor a caminho - 30 s ao vivo, 2 min atrasado, depois sem sinal`() {
        assertEquals(Frescor.AO_VIVO, Campo.frescor(ha(20), ModoRastreio.DESLOCAMENTO, agora))
        assertEquals(Frescor.ATRASADO, Campo.frescor(ha(60), ModoRastreio.DESLOCAMENTO, agora))
        assertEquals(Frescor.SEM_SINAL, Campo.frescor(ha(200), ModoRastreio.DESLOCAMENTO, agora))
    }

    @Test
    fun `frescor no local - folgado, porque o celular manda de minuto em minuto`() {
        assertEquals(Frescor.AO_VIVO, Campo.frescor(ha(60), ModoRastreio.ECO, agora))
        assertEquals(Frescor.ATRASADO, Campo.frescor(ha(200), ModoRastreio.ECO, agora))
        assertEquals(Frescor.SEM_SINAL, Campo.frescor(ha(500), ModoRastreio.ECO, agora))
    }

    @Test
    fun `texto do frescor`() {
        fun p(s: Long, modo: ModoRastreio = ModoRastreio.DESLOCAMENTO) = PosicaoCampo(-10.9, -37.0, 8.0, 10.0, 60, modo, ha(s))
        assertEquals("ao vivo", Campo.textoFrescor(p(5), agora))
        assertEquals("há 45 s", Campo.textoFrescor(p(45), agora))
        assertEquals("há 6 min · sem sinal", Campo.textoFrescor(p(360), agora))
        assertEquals("há 2 h · sem sinal", Campo.textoFrescor(p(7300, ModoRastreio.ECO), agora))
        assertEquals("esperando o GPS", Campo.textoFrescor(null, agora))
    }

    @Test
    fun `quando guardar um ponto`() {
        assertTrue("o primeiro sempre", Campo.deveGuardar(null, 0, 8f, ModoRastreio.DESLOCAMENTO))
        assertFalse("o GPS respondendo duas vezes no mesmo segundo", Campo.deveGuardar(1_000, 1_800, 8f, ModoRastreio.DESLOCAMENTO))
        assertTrue("a caminho, de 5 em 5 s", Campo.deveGuardar(0, 5_000, 8f, ModoRastreio.DESLOCAMENTO))
        assertFalse("no local, 30 s ainda não", Campo.deveGuardar(0, 30_000, 60f, ModoRastreio.ECO))
        assertTrue("no local, depois de 45 s", Campo.deveGuardar(0, 45_000, 60f, ModoRastreio.ECO))
        assertFalse("leitura de 900 m não serve nem para a posição ao vivo", Campo.deveGuardar(null, 0, 900f, ModoRastreio.ECO))
        assertTrue("aparelho que não informa a precisão continua valendo", Campo.deveGuardar(null, 0, null, ModoRastreio.ECO))
    }

    @Test
    fun `o modo segue a situacao da OS`() {
        assertEquals(ModoRastreio.DESLOCAMENTO, ModoRastreio.para(StatusOs.EM_DESLOCAMENTO))
        assertEquals(ModoRastreio.ECO, ModoRastreio.para(StatusOs.EM_ANDAMENTO))
        assertNull("pausada não rastreia", ModoRastreio.para(StatusOs.AGUARDANDO_PECA))
        assertNull(ModoRastreio.para(StatusOs.AGENDADA))
        assertNull(ModoRastreio.para(StatusOs.CONCLUIDA))
    }

    @Test
    fun `distancia e o jeito de escrever`() {
        val km = Campo.distanciaMetros(Coordenada(-10.9115, -37.0514), Coordenada(-10.6833, -37.4253)) / 1000
        assertTrue("Aracaju → Itabaiana ≈ 48 km em linha reta ($km)", km in 45.0..50.0)
        assertEquals("350 m", Campo.metros(353))
        assertEquals("2,3 km", Campo.metros(2_310))
    }

    @Test
    fun `velocidade so aparece andando`() {
        assertEquals(42, PosicaoCampo(0.1, 0.1, 8.0, 11.7, null, ModoRastreio.DESLOCAMENTO, agora).kmh)
        assertNull(PosicaoCampo(0.1, 0.1, 8.0, 0.4, null, ModoRastreio.DESLOCAMENTO, agora).kmh)
        assertNull("no local não mostra velocidade", PosicaoCampo(0.1, 0.1, 80.0, 3.0, null, ModoRastreio.ECO, agora).kmh)
    }
}
