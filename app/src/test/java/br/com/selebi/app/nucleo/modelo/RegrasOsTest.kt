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

    // --- observação obrigatória, foto no lugar da explicação, "agendar" -------------
    // Os mesmos casos de `src/os/testar-regras.ts` do servidor, um por um.

    private fun regra(
        tipo: TipoResposta = TipoResposta.TEXTO,
        fotos: Int = 0,
        exige: ExigeObservacao = ExigeObservacao.NAO,
        agendavel: Boolean = false,
    ) = ItemChecklist(
        "i", 1, "Item", obrigatorio = true, concluido = false, observacao = null, tipoResposta = tipo,
        fotosMinimas = fotos, exigeObservacao = exige, permiteAgendar = agendavel,
    )

    @Test
    fun `sem regra nova, tudo como era`() {
        assertTrue(RegrasOs.cumprido(regra(), Resposta.Texto("x"), 0))
        assertFalse(RegrasOs.cumprido(regra(), null, 0))
        assertFalse(RegrasOs.cumprido(regra(fotos = 1), Resposta.Texto("x"), 0))
        assertTrue(RegrasOs.cumprido(regra(fotos = 1), Resposta.Texto("x"), 1))
        assertFalse(RegrasOs.cumprido(regra(TipoResposta.CHECK), Resposta.Marcado(false), 0))
        assertTrue(RegrasOs.cumprido(regra(TipoResposta.SIM_NAO), Resposta.Marcado(false), 0))
        assertFalse(RegrasOs.cumprido(regra(TipoResposta.MULTIPLA), Resposta.Lista(emptyList()), 0))
        assertTrue(RegrasOs.cumprido(regra(TipoResposta.FOTO), null, 1))
        assertFalse(RegrasOs.cumprido(regra(TipoResposta.FOTO), null, 0))
    }

    @Test
    fun `sem foto, a observacao vale no lugar da foto`() {
        val numero = regra(TipoResposta.NUMERO, fotos = 1, exige = ExigeObservacao.SEM_FOTO)
        val valor = Resposta.Numero(40.0)
        assertFalse(RegrasOs.cumprido(numero, valor, 0, observacao = null))
        assertTrue(RegrasOs.cumprido(numero, valor, 0, observacao = "poste na frente, sem como fotografar"))
        assertTrue(RegrasOs.cumprido(numero, valor, 1, observacao = null))
        assertFalse(RegrasOs.cumprido(numero, valor, 0, observacao = "   "))
        assertFalse(RegrasOs.cumprido(numero, null, 0, observacao = "explicação, mas sem a resposta"))

        val fachada = regra(TipoResposta.FOTO, fotos = 1, exige = ExigeObservacao.SEM_FOTO)
        assertFalse(RegrasOs.cumprido(fachada, null, 0, observacao = null))
        assertTrue(RegrasOs.cumprido(fachada, null, 0, observacao = "portão fechado, ninguém atendeu"))
    }

    @Test
    fun `sempre pede a observacao com foto ou sem, e ela nao substitui a foto`() {
        val aterramento = regra(TipoResposta.ESCOLHA, fotos = 1, exige = ExigeObservacao.SEMPRE)
        val valor = Resposta.Texto("Não existe")
        assertFalse(RegrasOs.cumprido(aterramento, valor, 1, observacao = null))
        assertFalse(RegrasOs.cumprido(aterramento, valor, 0, observacao = "sem haste"))
        assertTrue(RegrasOs.cumprido(aterramento, valor, 1, observacao = "sem haste, cabo solto"))

        val rasgar = regra(TipoResposta.SIM_NAO, exige = ExigeObservacao.SEMPRE)
        assertFalse(RegrasOs.cumprido(rasgar, Resposta.Marcado(true), 0, observacao = null))
        assertTrue(RegrasOs.cumprido(rasgar, Resposta.Marcado(false), 0, observacao = "o cabo passa pelo forro"))
        assertFalse(RegrasOs.cumprido(rasgar, null, 0, observacao = "só a observação"))
    }

    @Test
    fun `agendar deixa o item para a visita de volta, mas so onde o item permite`() {
        val sombra = regra(TipoResposta.ESCOLHA, fotos = 1, exige = ExigeObservacao.SEM_FOTO, agendavel = true)
        assertTrue(RegrasOs.cumprido(sombra, null, 0, observacao = null, agendar = true))
        assertFalse(RegrasOs.cumprido(regra(TipoResposta.ESCOLHA, fotos = 1), null, 0, observacao = null, agendar = true))
        assertFalse(RegrasOs.cumprido(sombra, null, 0, observacao = null, agendar = false))
    }

    @Test
    fun `o que falta, em palavras, e igual ao do servidor`() {
        val aterramento = regra(TipoResposta.ESCOLHA, fotos = 1, exige = ExigeObservacao.SEMPRE)
        assertEquals("Falta a resposta, 1 foto e a observação", RegrasOs.faltaNoItem(aterramento, 0))
        assertEquals(
            "Falta a observação",
            RegrasOs.faltaNoItem(aterramento.copy(valor = Resposta.Texto("Existe e está bom")), 1),
        )
        val semFoto = regra(TipoResposta.NUMERO, fotos = 1, exige = ExigeObservacao.SEM_FOTO).copy(valor = Resposta.Numero(40.0))
        assertEquals("Falta 1 foto (ou a observação do porquê)", RegrasOs.faltaNoItem(semFoto, 0))
        assertNull(RegrasOs.faltaNoItem(semFoto.copy(observacao = "sem acesso"), 0))
        assertEquals("Falta marcar como feito", RegrasOs.faltaNoItem(regra(TipoResposta.CHECK), 0))
        assertEquals("Falta 1 foto", RegrasOs.faltaNoItem(regra(fotos = 2).copy(valor = Resposta.Texto("x")), 1))
    }

    @Test
    fun `a observacao e obrigatoria so quando a regra manda`() {
        val semFoto = regra(TipoResposta.NUMERO, fotos = 1, exige = ExigeObservacao.SEM_FOTO)
        assertTrue(RegrasOs.observacaoObrigatoria(semFoto, fotos = 0))
        assertFalse(RegrasOs.observacaoObrigatoria(semFoto, fotos = 1))
        assertTrue(RegrasOs.observacaoObrigatoria(regra(exige = ExigeObservacao.SEMPRE), fotos = 3))
        assertFalse(RegrasOs.observacaoObrigatoria(regra(), fotos = 0))
    }
}
