package br.com.selebi.app.nucleo.util

private val PARTICULAS = setOf("da", "das", "de", "di", "do", "dos", "e")

/** "Vinícius Lima dos Santos" → "VS". Ignora "da", "de", "dos". */
fun iniciais(nome: String): String {
    val partes = nome.trim().split(Regex("\\s+"))
        .filter { it.isNotEmpty() && it.lowercase() !in PARTICULAS }
    val primeira = partes.firstOrNull()?.take(1).orEmpty()
    val ultima = if (partes.size > 1) partes.last().take(1) else ""
    return (primeira + ultima).uppercase()
}

/** 79998959115 → (79) 99895-9115. Número fora do padrão sai como veio. */
fun formatarTelefone(digitos: String): String = when (digitos.length) {
    11 -> "(${digitos.substring(0, 2)}) ${digitos.substring(2, 7)}-${digitos.substring(7)}"
    10 -> "(${digitos.substring(0, 2)}) ${digitos.substring(2, 6)}-${digitos.substring(6)}"
    else -> digitos
}

/** "1 OS", "3 OS" — "ordem" no singular e plural sem virar texto longo na tela. */
fun contarOs(quantidade: Int): String = if (quantidade == 1) "1 OS" else "$quantidade OS"
