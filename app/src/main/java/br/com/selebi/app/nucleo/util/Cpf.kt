package br.com.selebi.app.nucleo.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * CPF: o login do app.
 *
 * O campo guarda só os dígitos; a máscara 000.000.000-00 é desenho, feita por
 * [MascaraCpf]. Validar os dígitos verificadores no celular pega o erro de
 * digitação antes de ir ao servidor — e o botão Entrar só acende com um CPF
 * que pode existir.
 */
object Cpf {
    fun digitos(texto: String): String = texto.filter(Char::isDigit).take(11)

    fun valido(cpf: String): Boolean {
        if (cpf.length != 11 || cpf.any { !it.isDigit() }) return false
        // 111.111.111-11 passa no cálculo e não é CPF de ninguém.
        if (cpf.all { it == cpf[0] }) return false
        return verificador(cpf, 9) == cpf[9].digitToInt() &&
            verificador(cpf, 10) == cpf[10].digitToInt()
    }

    private fun verificador(cpf: String, tamanho: Int): Int {
        var soma = 0
        for (i in 0 until tamanho) soma += cpf[i].digitToInt() * (tamanho + 1 - i)
        val resto = (soma * 10) % 11
        return if (resto == 10) 0 else resto
    }

    fun formatar(cpf: String): String =
        if (cpf.length == 11) {
            "${cpf.substring(0, 3)}.${cpf.substring(3, 6)}.${cpf.substring(6, 9)}-${cpf.substring(9)}"
        } else {
            cpf
        }
}

/**
 * Máscara visual do CPF: o usuário digita números e vê 123.456.789-09.
 *
 * O mapeamento de posição é o que mantém o cursor no lugar certo quando a
 * pessoa apaga um dígito no meio — sem ele, o cursor pula para o fim.
 */
class MascaraCpf : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val d = text.text
        val formatado = buildString {
            d.forEachIndexed { i, c ->
                append(c)
                if ((i == 2 || i == 5) && i < d.lastIndex) append('.')
                if (i == 8 && i < d.lastIndex) append('-')
            }
        }
        val mapa = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                offset + when {
                    offset > 9 -> 3
                    offset > 6 -> 2
                    offset > 3 -> 1
                    else -> 0
                }

            override fun transformedToOriginal(offset: Int): Int =
                (
                    offset - when {
                        offset > 11 -> 3
                        offset > 7 -> 2
                        offset > 3 -> 1
                        else -> 0
                    }
                    ).coerceIn(0, d.length)
        }
        return TransformedText(AnnotatedString(formatado), mapa)
    }
}
