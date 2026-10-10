package br.com.selebi.app.nucleo.util

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime

/**
 * Datas escritas do jeito que se fala em Sergipe: "Hoje, 09:00", "sex, 27 de set".
 *
 * Os nomes dos dias e dos meses estão aqui, e não vêm do sistema, porque este
 * código roda no Android e no iPhone — e cada um tem a sua ideia de "português"
 * (o ponto depois de "set." e o "-feira" mudam de um para o outro).
 */
object Datas {
    private val diasLongos = listOf("segunda", "terça", "quarta", "quinta", "sexta", "sábado", "domingo")
    private val diasCurtos = listOf("seg", "ter", "qua", "qui", "sex", "sáb", "dom")
    private val mesesLongos = listOf(
        "janeiro", "fevereiro", "março", "abril", "maio", "junho",
        "julho", "agosto", "setembro", "outubro", "novembro", "dezembro",
    )
    private val mesesCurtos = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")

    /** O índice do dia da semana, de 0 (segunda) a 6 (domingo). */
    private fun indiceDoDia(dia: LocalDate): Int = dia.dayOfWeek.ordinal

    /** "quinta, 25 de setembro" — sem o "-feira", que só ocupa espaço. */
    fun diaPorExtenso(dia: LocalDate): String =
        "${diasLongos[indiceDoDia(dia)]}, ${dia.dayOfMonth} de ${mesesLongos[dia.monthNumber - 1]}"

    /** "Hoje", "Amanhã", "Ontem" ou "sex, 27 de set". */
    fun rotuloDia(dia: LocalDate, hoje: LocalDate): String = when (dia) {
        hoje -> "Hoje"
        hoje.plus(1, DateTimeUnit.DAY) -> "Amanhã"
        hoje.minus(1, DateTimeUnit.DAY) -> "Ontem"
        else -> "${diasCurtos[indiceDoDia(dia)]}, ${dia.dayOfMonth} de ${mesesCurtos[dia.monthNumber - 1]}"
    }

    /** "S", "T", "Q"… — a inicial do dia, para o cabeçalho da agenda. */
    fun inicialDoDia(dia: LocalDate): String = diasCurtos[indiceDoDia(dia)].take(1).uppercase()

    fun hora(momento: LocalDateTime): String = "${momento.hour.dois()}:${momento.minute.dois()}"

    /** "Hoje, 09:00" — meia-noite em ponto vira só o dia, que é "o dia todo". */
    fun quando(momento: LocalDateTime, hoje: LocalDate): String {
        val dia = rotuloDia(momento.date, hoje)
        return if (momento.hour == 0 && momento.minute == 0) dia else "$dia, ${hora(momento)}"
    }

    /** "Setembro de 2026". */
    fun mesEAno(dia: LocalDate): String =
        "${mesesLongos[dia.monthNumber - 1].replaceFirstChar { it.uppercase() }} de ${dia.year}"

    /** "25/09 às 14:30", no fuso do aparelho. */
    fun instanteCurto(instante: Instant, fuso: TimeZone = TimeZone.currentSystemDefault()): String {
        val m = instante.toLocalDateTime(fuso)
        return "${m.dayOfMonth.dois()}/${m.monthNumber.dois()} às ${hora(m)}"
    }

    /** "agora há pouco", "há 5 min", "há 3 h", "há 2 dias". */
    @OptIn(ExperimentalTime::class)
    fun haQuanto(instante: Instant, agora: Instant = agoraMesmo()): String {
        val passou = agora - instante
        val minutos = passou.inWholeMinutes
        return when {
            minutos < 2 -> "agora há pouco"
            minutos < 60 -> "há $minutos min"
            passou.inWholeHours < 24 -> "há ${passou.inWholeHours} h"
            passou.inWholeDays == 1L -> "ontem"
            else -> "há ${passou.inWholeDays} dias"
        }
    }

    fun saudacao(hora: Int): String = when (hora) {
        in 5..11 -> "Bom dia"
        in 12..17 -> "Boa tarde"
        else -> "Boa noite"
    }

    fun lerRelogio(texto: String?): LocalDateTime? =
        texto?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() }

    fun lerInstante(texto: String?): Instant? =
        texto?.let { runCatching { Instant.parse(it) }.getOrNull() }

    /** O instante de agora. Num lugar só, para os testes poderem passar o deles. */
    fun agoraMesmo(): Instant = kotlin.time.Clock.System.now()

    /** Agora, em milissegundos — o carimbo da fila e das posições guardadas. */
    fun agoraMs(): Long = agoraMesmo().toEpochMilliseconds()

    /** O dia de hoje, no fuso do aparelho. */
    fun hoje(fuso: TimeZone = TimeZone.currentSystemDefault()): LocalDate = agoraRelogio(fuso).date

    /** A hora de relógio de agora, no fuso do aparelho — o que o técnico vê na parede. */
    fun agoraRelogio(fuso: TimeZone = TimeZone.currentSystemDefault()): LocalDateTime =
        agoraMesmo().toLocalDateTime(fuso)

    /** A segunda-feira da semana deste dia (ou ele mesmo, se já for segunda). */
    fun segundaDaSemana(dia: LocalDate): LocalDate = dia.minus(dia.dayOfWeek.ordinal, DateTimeUnit.DAY)

    private fun Int.dois(): String = toString().padStart(2, '0')
}

fun LocalDate.plusDays(n: Long): LocalDate = plus(n, DateTimeUnit.DAY)

fun LocalDate.minusDays(n: Long): LocalDate = minus(n, DateTimeUnit.DAY)
