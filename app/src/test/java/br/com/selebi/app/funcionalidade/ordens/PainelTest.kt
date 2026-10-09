package br.com.selebi.app.funcionalidade.ordens

import br.com.selebi.app.amostras.Amostras
import br.com.selebi.app.nucleo.modelo.StatusOs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A tela inicial é o trunfo do app; a regra dela vive em `montarPainel` e é
 * conferida aqui com as OS de amostra (sexta, 25/09/2026, 9h40).
 */
class PainelTest {
    private val agora = Amostras.agora
    private val ordens = Amostras.ordens()

    private fun painel(
        filtro: FiltroOrdens = FiltroOrdens.TODAS,
        busca: String = "",
        escopo: EscopoOrdens = EscopoOrdens.EQUIPE,
        usuarioId: String? = null,
    ) = montarPainel(ordens, usuarioId, escopo, filtro, busca, agora)

    @Test
    fun `destaque e a OS em andamento`() {
        val destaque = painel().destaque
        assertEquals(TipoDestaque.AGORA, destaque?.tipo)
        assertEquals(47, destaque?.ordem?.numero)
    }

    @Test
    fun `sem nada em andamento, o destaque e a proxima de hoje`() {
        val semAndamento = ordens.filter { it.status != StatusOs.EM_ANDAMENTO }
        val destaque = montarPainel(semAndamento, null, EscopoOrdens.EQUIPE, FiltroOrdens.TODAS, "", agora).destaque
        assertEquals(TipoDestaque.PROXIMA, destaque?.tipo)
        assertEquals(49, destaque?.ordem?.numero)
    }

    @Test
    fun `contagem do dia`() {
        val c = painel().contagem
        // A em andamento (#47, marcada para as 8h) conta em "Hoje" e em "Em campo".
        assertEquals(2, c.hoje)
        assertEquals(1, c.atrasadas)
        assertEquals(1, c.emCampo)
        assertEquals(1, c.concluidas)
        assertEquals(7, c.abertas)
    }

    @Test
    fun `secoes na ordem de urgencia, sem repetir o destaque`() {
        val secoes = painel().secoes
        assertEquals(listOf("Atrasadas", "Ainda hoje", "Amanhã", "dom, 27 de set", "Sem data marcada"), secoes.map { it.titulo })
        assertEquals(listOf(41), secoes[0].ordens.map { it.numero })
        assertTrue(secoes.none { s -> s.ordens.any { it.numero == 47 } })
        // Concluída não entra na lista principal.
        assertTrue(secoes.none { s -> s.ordens.any { it.numero == 44 } })
    }

    @Test
    fun `sem data ordena por prioridade`() {
        val semData = painel().secoes.first { it.chave == "sem-data" }
        assertEquals(listOf(39, 46), semData.ordens.map { it.numero })
    }

    @Test
    fun `filtro atrasadas mostra so elas e sem destaque`() {
        val p = painel(filtro = FiltroOrdens.ATRASADAS)
        assertNull(p.destaque)
        assertEquals(listOf(41), p.secoes.single().ordens.map { it.numero })
    }

    @Test
    fun `filtro hoje e o dia inteiro, com a em andamento, e bate com o numero do topo`() {
        val p = painel(filtro = FiltroOrdens.HOJE)
        assertEquals(listOf(47, 49), p.secoes.single().ordens.map { it.numero })
        assertEquals(p.contagem.hoje, p.secoes.single().ordens.size)
    }

    @Test
    fun `filtro concluidas`() {
        assertEquals(listOf(44), painel(filtro = FiltroOrdens.CONCLUIDAS).secoes.single().ordens.map { it.numero })
    }

    @Test
    fun `busca ignora acento e maiuscula`() {
        assertEquals(listOf(41), painel(busca = "MERCADO sao").secoes.single().ordens.map { it.numero })
        assertEquals(setOf(41, 49), painel(busca = "são josé").secoes.single().ordens.map { it.numero }.toSet())
    }

    @Test
    fun `busca pelo numero da OS, com ou sem cerquilha e zeros`() {
        assertEquals(listOf(49), painel(busca = "#0049").secoes.single().ordens.map { it.numero })
        assertEquals(listOf(49), painel(busca = "49").secoes.single().ordens.map { it.numero })
    }

    @Test
    fun `busca acha concluida tambem`() {
        assertEquals(listOf(44), painel(busca = "manuino").secoes.single().ordens.map { it.numero })
    }

    @Test
    fun `so as minhas filtra pelo responsavel`() {
        val p = painel(escopo = EscopoOrdens.MINHAS, usuarioId = "u3")
        val numeros = listOfNotNull(p.destaque?.ordem?.numero) + p.secoes.flatMap { s -> s.ordens.map { it.numero } }
        assertEquals(setOf(49, 51), numeros.toSet())
    }

    @Test
    fun `OS de ontem nao feita vira atrasada, a de hoje cedo nao`() {
        val ontem = ordens.first { it.numero == 50 }.copy(agendadaPara = agora.minusDays(1))
        assertTrue(ontem.atrasada(agora))
        val hojeCedo = ordens.first { it.numero == 50 }.copy(agendadaPara = agora.withHour(7))
        assertFalse(hojeCedo.atrasada(agora))
    }

    @Test
    fun `lista vazia da painel vazio`() {
        assertTrue(montarPainel(emptyList(), null, EscopoOrdens.EQUIPE, FiltroOrdens.TODAS, "", agora).vazio)
    }
}
