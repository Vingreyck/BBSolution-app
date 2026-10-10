package br.com.selebi.app.nucleo.util

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
