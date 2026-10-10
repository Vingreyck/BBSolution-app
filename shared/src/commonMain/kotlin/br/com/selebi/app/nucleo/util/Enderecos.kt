package br.com.selebi.app.nucleo.util

import br.com.selebi.app.nucleo.plataforma.Ambiente

/** Endereços do Selebi fora da API — o relatório público, por exemplo. */
object Enderecos {

    /**
     * `/r/abc…` → `https://servidor/r/abc…`, no mesmo servidor da API: se o
     * domínio mudar, o endereço muda junto com o da API.
     */
    fun publico(caminho: String, base: String = Ambiente.apiUrl): String {
        val inicioHost = base.indexOf("://").let { if (it < 0) 0 else it + 3 }
        val fimHost = base.indexOf('/', inicioHost).let { if (it < 0) base.length else it }
        return base.substring(0, fimHost) + "/" + caminho.trimStart('/')
    }
}

/**
 * Texto pronto para ir num endereço (`?text=` do WhatsApp, a busca do mapa):
 * letras, números e `-_.~` ficam; o resto vira `%XX` dos bytes em UTF-8.
 */
fun codificarUrl(texto: String): String = buildString {
    for (byte in texto.encodeToByteArray()) {
        val c = byte.toInt() and 0xFF
        val ch = c.toChar()
        if (c < 0x80 && (ch.isLetterOrDigit() || ch in "-_.~")) {
            append(ch)
        } else {
            append('%')
            append(HEX[c shr 4])
            append(HEX[c and 0x0F])
        }
    }
}

private const val HEX = "0123456789ABCDEF"
