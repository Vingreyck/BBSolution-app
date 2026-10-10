package br.com.selebi.app.amostras

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.plus
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

/*
 * As amostras (dados de mentira para as capturas de tela) foram escritas com as
 * contas de data do Java: `agora.minusDays(1).withHour(18)`. Estas funções dão
 * os mesmos nomes ao `LocalDateTime` comum, para elas não precisarem ser
 * reescritas conta por conta.
 */

fun LocalDateTime.plusDays(n: Long): LocalDateTime = date.plus(n, DateTimeUnit.DAY).atTime(hour, minute, second)

fun LocalDateTime.minusDays(n: Long): LocalDateTime = plusDays(-n)

fun LocalDateTime.plusMinutes(n: Long): LocalDateTime =
    toInstant(TimeZone.UTC).plus(n, DateTimeUnit.MINUTE).toLocalDateTime(TimeZone.UTC)

fun LocalDateTime.minusMinutes(n: Long): LocalDateTime = plusMinutes(-n)

fun LocalDateTime.minusSeconds(n: Long): LocalDateTime =
    toInstant(TimeZone.UTC).plus(-n, DateTimeUnit.SECOND).toLocalDateTime(TimeZone.UTC)

fun LocalDateTime.minusHours(n: Long): LocalDateTime = plusMinutes(-n * 60)

fun LocalDateTime.withHour(hora: Int): LocalDateTime = date.atTime(hora, minute, second)

fun LocalDateTime.withMinute(minuto: Int): LocalDateTime = date.atTime(hour, minuto, second)
