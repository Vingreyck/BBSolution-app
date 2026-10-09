package br.com.selebi.app.nucleo.util

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Datas escritas do jeito que se fala em Sergipe: "Hoje, 09:00", "sex, 27 de set". */
object Datas {
    val brasil: Locale = Locale.forLanguageTag("pt-BR")

    private val diaLongo = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", brasil)
    private val diaCurto = DateTimeFormatter.ofPattern("EEE, d 'de' MMM", brasil)
    private val mesAno = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", brasil)
    private val diaMesHora = DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm", brasil)

    /** "quinta, 25 de setembro" — sem o "-feira", que só ocupa espaço. */
    fun diaPorExtenso(dia: LocalDate): String = dia.format(diaLongo).replace("-feira", "")

    /** "Hoje", "Amanhã", "Ontem" ou "sex, 27 de set". */
    fun rotuloDia(dia: LocalDate, hoje: LocalDate): String = when (dia) {
        hoje -> "Hoje"
        hoje.plusDays(1) -> "Amanhã"
        hoje.minusDays(1) -> "Ontem"
        else -> dia.format(diaCurto).replace("-feira", "").replace(".", "")
    }

    fun hora(momento: LocalDateTime): String = "%02d:%02d".format(momento.hour, momento.minute)

    /** "Hoje, 09:00" — meia-noite em ponto vira só o dia, que é "o dia todo". */
    fun quando(momento: LocalDateTime, hoje: LocalDate): String {
        val dia = rotuloDia(momento.toLocalDate(), hoje)
        return if (momento.hour == 0 && momento.minute == 0) dia else "$dia, ${hora(momento)}"
    }

    fun mesEAno(dia: LocalDate): String = dia.format(mesAno).replaceFirstChar { it.uppercase() }

    fun instanteCurto(instante: Instant, fuso: ZoneId = ZoneId.systemDefault()): String =
        instante.atZone(fuso).format(diaMesHora)

    /** "agora há pouco", "há 5 min", "há 3 h", "há 2 dias". */
    fun haQuanto(instante: Instant, agora: Instant = Instant.now()): String {
        val passou = Duration.between(instante, agora)
        return when {
            passou.toMinutes() < 2 -> "agora há pouco"
            passou.toMinutes() < 60 -> "há ${passou.toMinutes()} min"
            passou.toHours() < 24 -> "há ${passou.toHours()} h"
            passou.toDays() == 1L -> "ontem"
            else -> "há ${passou.toDays()} dias"
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
}
