package pl.brulion.ui.common

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Polish = Locale.forLanguageTag("pl-PL")
private val DayLong = DateTimeFormatter.ofPattern("EEEE, d MMMM", Polish)
private val DayShort = DateTimeFormatter.ofPattern("d MMM", Polish)
private val DayShortWithYear = DateTimeFormatter.ofPattern("d MMM yyyy", Polish)
private val WeekdayShort = DateTimeFormatter.ofPattern("EEE", Polish)
private val Time = DateTimeFormatter.ofPattern("HH:mm", Polish)

fun epochToLocal(seconds: Long): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochSecond(seconds), ZoneId.systemDefault())

/** "środa, 28 października" */
fun LocalDate.formatLong(): String = format(DayLong).replaceFirstChar { it.uppercase() }

/** "28 paź", with the year only when it isn't the current one. */
fun LocalDate.formatShort(): String =
    format(if (year == LocalDate.now().year) DayShort else DayShortWithYear)

/** "śr." */
fun LocalDate.formatWeekday(): String = format(WeekdayShort)

/** "Dziś", "Jutro", "Wczoraj" or a short date. */
fun LocalDate.formatRelative(today: LocalDate = LocalDate.now()): String = when (this) {
    today -> "Dziś"
    today.plusDays(1) -> "Jutro"
    today.minusDays(1) -> "Wczoraj"
    else -> formatShort()
}

/** Time for today, otherwise a relative day or date; used in message and announcement lists. */
fun formatTimestamp(seconds: Long): String {
    val dateTime = epochToLocal(seconds)
    return if (dateTime.toLocalDate() == LocalDate.now()) dateTime.format(Time) else dateTime.toLocalDate().formatRelative()
}

fun Double.formatAverage(): String = String.format(Polish, "%.2f", this)

/** Weight without a needless ".0". */
fun Double.formatWeight(): String = if (this % 1.0 == 0.0) toInt().toString() else toString()

/** Seen for the first time within the last day (and not part of the initial import). */
fun isFresh(firstSeenAt: Long, nowSeconds: Long = System.currentTimeMillis() / 1000): Boolean =
    firstSeenAt > 0 && nowSeconds - firstSeenAt < 24 * 60 * 60
