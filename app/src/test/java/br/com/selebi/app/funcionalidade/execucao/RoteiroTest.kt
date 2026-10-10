package br.com.selebi.app.funcionalidade.execucao

import br.com.selebi.app.amostras.AmostrasExecucao
import br.com.selebi.app.nucleo.modelo.AcaoOs
import br.com.selebi.app.nucleo.modelo.Envio
import br.com.selebi.app.nucleo.modelo.StatusOs
import br.com.selebi.app.nucleo.modelo.TipoEnvio
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.datetime.Instant

/** O wizard sem tela: quais passos, onde abre, para onde pula. */
class RoteiroTest {
    private val agendada = AmostrasExecucao.vistoriaAgendada()
    private val emCampo = AmostrasExecucao.vistoriaEmCampo()

    @Test
    fun `chegada, um passo por item e a conclusao`() {
        val passos = Roteiro.passos(emCampo)
        assertEquals(emCampo.checklist.size + 2, passos.size)
        assertEquals(Passo.Chegada, passos.first())
        assertEquals(Passo.Conclusao, passos.last())
        assertEquals(Passo.Item("v05", 5, 17), passos[5])
    }

    @Test
    fun `fora de atendimento abre na chegada e nao deixa responder`() {
        assertEquals(0, Roteiro.passoInicial(agendada))
        assertFalse(Roteiro.podeResponder(agendada))
        assertFalse(Roteiro.podeResponder(emCampo.copy(status = StatusOs.AGUARDANDO_PECA)))
        assertTrue(Roteiro.podeResponder(emCampo))
    }

    @Test
    fun `em atendimento abre no primeiro obrigatorio que falta`() {
        // v05, o disjuntor: tem resposta, mas falta a foto.
        assertEquals(5, Roteiro.passoInicial(emCampo))
    }

    @Test
    fun `tocar num item do detalhe abre nele`() {
        assertEquals(12, Roteiro.passoInicial(agendada, itemId = "v12"))
        assertEquals(0, Roteiro.passoInicial(agendada, itemId = "nao-existe"))
    }

    @Test
    fun `tudo cumprido abre na conclusao`() {
        val pronta = emCampo.copy(checklist = emCampo.checklist.map { it.copy(concluido = true) })
        assertEquals(Roteiro.passos(pronta).lastIndex, Roteiro.passoInicial(pronta))
    }

    @Test
    fun `proximo pendente da a volta no checklist`() {
        assertEquals(7, Roteiro.proximoPendente(emCampo, depoisDe = 5))
        // Do último item, volta para o começo: o primeiro obrigatório que falta é o v05.
        assertEquals(5, Roteiro.proximoPendente(emCampo, depoisDe = 17))
        val pronta = emCampo.copy(checklist = emCampo.checklist.map { it.copy(concluido = true) })
        assertNull(Roteiro.proximoPendente(pronta, depoisDe = 3))
    }

    @Test
    fun `indice agrupa por secao na ordem do modelo`() {
        val secoes = Roteiro.secoes(emCampo)
        assertEquals(
            listOf("Cliente e unidade", "Padrão de entrada", "Quadro e cabos", "Telhado", "Inversor e futuro"),
            secoes.map { it.first },
        )
        assertEquals(listOf(4, 5, 6, 7, 8), secoes[1].second.map { it.first })
    }

    @Test
    fun `rotulo do passo`() {
        assertEquals("Item 5 de 17 · Padrão de entrada", Roteiro.rotulo(Roteiro.passos(emCampo)[5], emCampo))
    }

    @Test
    fun `fila em palavras`() {
        fun envio(tipo: TipoEnvio, itemId: String? = null, acao: AcaoOs? = null) =
            Envio("x", emCampo.id, tipo, itemId, acao, Instant.fromEpochSeconds(0), null)
        assertEquals("Foto · Disjuntor do padrão — amperagem", Roteiro.descrever(envio(TipoEnvio.FOTO, "v05"), emCampo))
        assertEquals("Conclusão da OS", Roteiro.descrever(envio(TipoEnvio.ACAO, acao = AcaoOs.CONCLUIR), emCampo))
        assertEquals("Assinatura do cliente", Roteiro.descrever(envio(TipoEnvio.ASSINATURA), emCampo))
    }

    @Test
    fun `mensagens prontas para o cliente`() {
        assertEquals(
            "Olá, Antônio! Aqui é Diego, da BB Soluções. Estou a caminho para a vistoria (OS #0049).",
            Roteiro.mensagemACaminho(agendada, "Diego Andrade", "BB Soluções"),
        )
        assertEquals(
            "Olá, Antônio! Estou a caminho para a vistoria (OS #0049).",
            Roteiro.mensagemACaminho(agendada, null, null),
        )
        assertTrue(
            Roteiro.mensagemRelatorio(agendada, "https://x/r/abc", "BB Soluções")
                .endsWith("(OS #0049), da BB Soluções:\nhttps://x/r/abc"),
        )
    }
}
