package br.com.selebi.app.nucleo.util

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Números escritos do jeito brasileiro: "1.234,5".
 *
 * Feito à mão, e não com `NumberFormat`, porque este código roda também no
 * iPhone, que não tem a classe do Java.
 */
object Numeros {
    /** Até [maxCasas] casas depois da vírgula, sem zeros sobrando: 40.0 → "40", 6.25 → "6,25". */
    fun brasileiro(valor: Double, maxCasas: Int = 3): String {
        if (valor.isNaN() || valor.isInfinite()) return valor.toString()
        val fator = 10.0.pow(maxCasas).roundToLong()
        val total = (abs(valor) * fator).roundToLong()
        val inteira = total / fator
        val decimais = (total % fator).toString().padStart(maxCasas, '0').trimEnd('0')
        val milhares = inteira.toString().reversed().chunked(3).joinToString(".").reversed()
        val sinal = if (valor < 0 && total != 0L) "-" else ""
        return sinal + milhares + if (decimais.isEmpty()) "" else ",$decimais"
    }

    /** Sempre [casas] casas, sem separar milhar: 12.5 com 2 → "12,50". O `%.2f` do Java. */
    fun fixo(valor: Double, casas: Int): String {
        if (valor.isNaN() || valor.isInfinite()) return valor.toString()
        val fator = 10.0.pow(casas).roundToLong()
        val total = (abs(valor) * fator).roundToLong()
        val sinal = if (valor < 0 && total != 0L) "-" else ""
        val inteira = (total / fator).toString()
        if (casas <= 0) return sinal + inteira
        return sinal + inteira + "," + (total % fator).toString().padStart(casas, '0')
    }

    /** Uma casa depois da vírgula, sempre: 2.34 → "2,3". Para "2,3 km". */
    fun umaCasa(valor: Double): String {
        val arredondado = (valor * 10).roundToLong()
        return "${arredondado / 10},${abs(arredondado % 10)}"
    }
}
