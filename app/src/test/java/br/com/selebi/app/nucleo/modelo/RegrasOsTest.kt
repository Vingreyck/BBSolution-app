package br.com.selebi.app.nucleo.modelo

import br.com.selebi.app.amostras.AmostrasExecucao
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * As regras que o app antecipa sem sinal. Cada caso aqui tem o gêmeo no
 * servidor (`src/os/respostas.ts`, `src/os/fluxo.ts`): se um mudar, o outro
 * precisa mudar junto, ou a fila leva para o servidor o que ele vai recusar.
 */
class RegrasOsTest {

    private fun item(tipo: TipoResposta, opcoes: List<String> = emptyList(), fotos: Int = 0, valor: Resposta? = null) =
        ItemChecklist("i", 1, "Item", obrigatorio = true, concluido = false, observacao = null, tipoResposta = tipo, opcoes = opcoes, fotosMinimas = fotos, valor = valor)

    private fun ok(n: RegrasOs.Normalizado): Resposta? = (n as RegrasOs.Normalizado.Ok).valor

    @Test
    fun `numero aceita virgula e ponto de milhar como o servidor`() {
        assertEquals(Resposta.Numero(1234.5), ok(RegrasOs.normalizar(item(TipoResposta.NUMERO), Resposta.Texto("1.234,5"))))
        assertEquals(Resposta.Numero(40.0), ok(RegrasOs.normalizar(item(TipoResposta.NUMERO), Resposta.Texto("40"))))
        assertNull(ok(RegrasOs.normalizar(item(TipoResposta.NUMERO), Resposta.Texto("  "))))
        assertTrue(RegrasOs.normalizar(item(TipoResposta.NUMERO), Resposta.Texto("quarenta")) is RegrasOs.Normalizado.Recusa)
        assertTrue(RegrasOs.normalizar(item(TipoResposta.NUMERO), Resposta.Numero(2e9)) is RegrasOs.Normalizado.Recusa)
    }

    @Test
    fun `escolha fora da lista e recusada`() {
        val polos = item(TipoResposta.ESCOLHA, listOf("Monopolar", "Bipolar", "Tripolar"))
        assertEquals(Resposta.Texto("Bipolar"), ok(RegrasOs.normalizar(polos, Resposta.Texto(" Bipolar "))))
        val recusa = RegrasOs.normalizar(polos, Resposta.Texto("Tetrapolar"))
        assertTrue(recusa is RegrasOs.Normalizado.Recusa)
        assertTrue((recusa as RegrasOs.Normalizado.Recusa).mensagem.contains("Tetrapolar"))
    }

    @Test
    fun `multipla fica so com opcoes validas, sem repetir, e vazia vira nula`() {
        val estado = item(TipoResposta.MULTIPLA, listOf("Bom", "Telhas quebradas"))
        assertEquals(
            Resposta.Lista(listOf("Telhas quebradas")),
            ok(RegrasOs.normalizar(estado, Resposta.Lista(listOf("Telhas quebradas", "Inventada", "Telhas quebradas")))),
        )
        assertNull(ok(RegrasOs.normalizar(estado, Resposta.Lista(listOf("Inventada")))))
    }

    @Test
    fun `serial sai maiusculo, sem espaco e sem repetir, e lixo e recusado`() {
        val serial = item(TipoResposta.SERIAL)
        assertEquals(
            Resposta.Lista(listOf("HMS2000A1B2C3")),
            ok(RegrasOs.normalizar(serial, Resposta.Lista(listOf("hms 2000 a1b2c3", "HMS2000A1B2C3")))),
        )
        assertTrue(RegrasOs.normalizar(serial, Resposta.Lista(listOf("AB"))) is RegrasOs.Normalizado.Recusa)
        assertTrue(RegrasOs.normalizar(serial, Resposta.Lista(listOf("ABC#123"))) is RegrasOs.Normalizado.Recusa)
        assertNull(RegrasOs.problemaNoSerial("GRT-5000/01"))
        assertEquals("Curto demais para um número de série.", RegrasOs.problemaNoSerial("AB1"))
    }

    @Test
    fun `serial dentro do que o leitor leu`() {
        assertEquals("ABC123456", RegrasOs.serialDoCodigo("https://server.growatt.com/q?sn=abc123456&pn=9"))
        assertEquals("XYZ98765", RegrasOs.serialDoCodigo("PN:1234 SN:XYZ98765"))
        assertEquals("HMS2000A1B2C3", RegrasOs.serialDoCodigo(" hms2000a1b2c3 "))
        // Serial que começa com "SN" não é confundido com um rótulo.
        assertEquals("SNA12345", RegrasOs.serialDoCodigo("SNA12345"))
    }

    @Test
    fun `cumprido segue o servidor tipo a tipo`() {
        assertFalse(RegrasOs.cumprido(item(TipoResposta.CHECK), Resposta.Marcado(false), 0))
        assertTrue(RegrasOs.cumprido(item(TipoResposta.CHECK), Resposta.Marcado(true), 0))
        // Sim/não vale qualquer uma das duas — "não tem aterramento" é resposta.
        assertTrue(RegrasOs.cumprido(item(TipoResposta.SIM_NAO), Resposta.Marcado(false), 0))
        assertFalse(RegrasOs.cumprido(item(TipoResposta.NUMERO, fotos = 1), Resposta.Numero(40.0), 0))
        assertTrue(RegrasOs.cumprido(item(TipoResposta.NUMERO, fotos = 1), Resposta.Numero(40.0), 1))
        assertFalse(RegrasOs.cumprido(item(TipoResposta.FOTO), null, 0))
        assertTrue(RegrasOs.cumprido(item(TipoResposta.FOTO), null, 1))
        assertFalse(RegrasOs.cumprido(item(TipoResposta.FOTO, fotos = 2), null, 1))
    }

    @Test
    fun `o que falta no item, em poucas palavras`() {
        assertEquals("Falta 1 foto", RegrasOs.faltaNoItem(item(TipoResposta.NUMERO, fotos = 1, valor = Resposta.Numero(40.0)), 0))
        assertEquals("Falta a resposta e 2 fotos", RegrasOs.faltaNoItem(item(TipoResposta.ESCOLHA, listOf("A"), fotos = 2), 0))
        assertEquals("Falta marcar como feito", RegrasOs.faltaNoItem(item(TipoResposta.CHECK), 0))
        assertEquals("Falta 1 foto", RegrasOs.faltaNoItem(item(TipoResposta.FOTO), 0))
        assertNull(RegrasOs.faltaNoItem(item(TipoResposta.SIM_NAO, valor = Resposta.Marcado(false)), 0))
    }

    @Test
    fun `motivo escrito como o site escreve`() {
        assertEquals("Cliente ausente", RegrasOs.motivoCom("Cliente ausente", " "))
        assertEquals("Cliente ausente — ninguém no portão", RegrasOs.motivoCom("Cliente ausente", "ninguém no portão"))
        assertEquals("Caminhão atolado", RegrasOs.motivoCom("Outro", "Caminhão atolado"))
        assertEquals("", RegrasOs.motivoCom("Outro", ""))
    }

    @Test
    fun `de onde cada acao parte e o PERMITIDO do servidor`() {
        assertTrue(RegrasOs.permitida(AcaoOs.DESLOCAMENTO, StatusOs.AGENDADA))
        assertFalse(RegrasOs.permitida(AcaoOs.DESLOCAMENTO, StatusOs.EM_ANDAMENTO))
        assertTrue(RegrasOs.permitida(AcaoOs.INICIAR, StatusOs.EM_DESLOCAMENTO))
        assertFalse(RegrasOs.permitida(AcaoOs.PAUSAR, StatusOs.AGENDADA))
        assertTrue(RegrasOs.permitida(AcaoOs.RETOMAR, StatusOs.AGUARDANDO_PECA))
        // Concluir parte de qualquer situação em aberto, como no servidor.
        assertTrue(RegrasOs.permitida(AcaoOs.CONCLUIR, StatusOs.AGUARDANDO_PECA))
        assertFalse(RegrasOs.permitida(AcaoOs.CONCLUIR, StatusOs.CONCLUIDA))
        assertFalse(RegrasOs.permitida(AcaoOs.NAO_ATENDIDA, StatusOs.ABERTA))
    }

    @Test
    fun `conclusao trava pelos mesmos motivos do servidor`() {
        val instalacao = AmostrasExecucao.instalacaoParaConcluir()
        val travas = RegrasOs.impedimentos(instalacao, resolvido = true, laudo = null, temAssinatura = false)
        assertEquals(2, travas.size)
        assertTrue(travas[0].startsWith("Faltam 2 itens obrigatórios"))
        assertEquals("Falta a assinatura do cliente.", travas[1])

        val pronta = instalacao.copy(checklist = instalacao.checklist.map { it.copy(concluido = true) })
        assertTrue(RegrasOs.impedimentos(pronta, resolvido = true, laudo = null, temAssinatura = true).isEmpty())
        // Não resolvido dispensa a assinatura, mas pede o laudo.
        assertEquals(
            listOf("Explique no laudo o que ficou pendente."),
            RegrasOs.impedimentos(pronta, resolvido = false, laudo = " ", temAssinatura = false),
        )
    }
}
