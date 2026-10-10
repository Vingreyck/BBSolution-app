package br.com.selebi.app.nucleo.util

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

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
