package br.com.selebi.app.nucleo.util

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CpfTest {

    @Test
    fun `aceita CPF com digitos verificadores certos`() {
        assertTrue(Cpf.valido("52998224725"))
        assertTrue(Cpf.valido("11144477735"))
    }

    @Test
    fun `recusa digito verificador errado, tamanho errado e numero repetido`() {
        assertFalse(Cpf.valido("52998224724"))
        assertFalse(Cpf.valido("5299822472"))
        assertFalse(Cpf.valido("529982247251"))
        assertFalse(Cpf.valido("11111111111"))
        assertFalse(Cpf.valido("5299822472a"))
    }

    @Test
    fun `digitos ignora pontuacao e corta em 11`() {
        assertEquals("52998224725", Cpf.digitos("529.982.247-25"))
        assertEquals("52998224725", Cpf.digitos("529982247251234"))
    }

    @Test
    fun `formata so quando tem 11 digitos`() {
        assertEquals("529.982.247-25", Cpf.formatar("52998224725"))
        assertEquals("529982", Cpf.formatar("529982"))
    }

    @Test
    fun `mascara desenha pontos e traco sem mudar o valor`() {
        val completo = MascaraCpf().filter(AnnotatedString("52998224725"))
        assertEquals("529.982.247-25", completo.text.text)

        val parcial = MascaraCpf().filter(AnnotatedString("5299"))
        assertEquals("529.9", parcial.text.text)
    }

    @Test
    fun `cursor fica no lugar certo nos dois sentidos`() {
        val mapa = MascaraCpf().filter(AnnotatedString("52998224725")).offsetMapping
        // Depois de 3 dígitos o cursor fica antes do ponto; depois de 4, depois dele.
        assertEquals(3, mapa.originalToTransformed(3))
        assertEquals(5, mapa.originalToTransformed(4))
        assertEquals(9, mapa.originalToTransformed(7))
        assertEquals(13, mapa.originalToTransformed(10))
        assertEquals(14, mapa.originalToTransformed(11))

        assertEquals(3, mapa.transformedToOriginal(4))
        assertEquals(6, mapa.transformedToOriginal(8))
        assertEquals(9, mapa.transformedToOriginal(12))
        assertEquals(11, mapa.transformedToOriginal(14))
    }
}
