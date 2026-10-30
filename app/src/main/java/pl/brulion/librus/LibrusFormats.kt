/*
 * Part of Brulion, GNU GPL v3. Based on Librus support in Szkolny.eu
 * (https://github.com/szkolny-eu/szkolny-android), (c) Kuba Szczodrzyński and contributors, GPL v3.
 */
package pl.brulion.librus

import kotlinx.serialization.json.JsonObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Librus sends local Warsaw time without a zone, e.g. "2026-10-28 14:05:00". */
private val LIBRUS_ZONE: ZoneId = ZoneId.of("Europe/Warsaw")

/**
 * The school's zone. Dates and lesson times are school time, so "today" and "now" for the
 * timetable come from here, also on a phone abroad. Notification times stay in the phone's zone.
 */
val SCHOOL_ZONE: ZoneId get() = LIBRUS_ZONE

fun schoolToday(): LocalDate = LocalDate.now(SCHOOL_ZONE)

/** "HH:mm" in school time, to compare with lesson start and end times. */
fun schoolTimeNow(): String = LocalTime.now(SCHOOL_ZONE).toString().take(5)

fun parseLibrusDateTime(value: String?): Long? = value?.let {
    runCatching { LocalDateTime.parse(it.take(19).replace(' ', 'T')).atZone(LIBRUS_ZONE).toEpochSecond() }.getOrNull()
}

fun parseLibrusDate(value: String?): LocalDate? = value?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }

/** "08:00:00" or "08:00" -> "08:00". */
fun parseLibrusTime(value: String?): String? = value?.takeIf { it.length >= 5 }?.take(5)

/** Id of a reference object like {"Id": 12, "Url": "..."}. */
fun JsonObject.refId(key: String): Long? = obj(key)?.long("Id")

/** Librus booleans come as true/false, but some endpoints use 1/0. */
fun JsonObject.flag(key: String): Boolean = bool(key) ?: (int(key) == 1)

/** Librus' fixed color palette, referenced by Color.Id in categories. */
fun librusColor(colorId: Int?): Int = when (colorId) {
    1 -> 0xFFF0E68C; 2 -> 0xFF87CEFA; 3 -> 0xFFB0C4DE; 4 -> 0xFFF0F8FF
    5 -> 0xFFF0FFFF; 6 -> 0xFFF5F5DC; 7 -> 0xFFFFEBCD; 8 -> 0xFFFFF8DC
    9 -> 0xFFA9A9A9; 10 -> 0xFFBDB76B; 11 -> 0xFF8FBC8F; 12 -> 0xFFDCDCDC
    13 -> 0xFFDAA520; 14 -> 0xFFE6E6FA; 15 -> 0xFFFFA07A; 16 -> 0xFF32CD32
    17, 18 -> 0xFF66CDAA; 19 -> 0xFFC0C0C0; 20 -> 0xFFD2B48C; 21 -> 0xFF3333FF
    22 -> 0xFF7B68EE; 23 -> 0xFFBA55D3; 24 -> 0xFFFFB6C1; 25 -> 0xFFFF1493
    26 -> 0xFFDC143C; 27 -> 0xFFFF0000; 28 -> 0xFFFF8C00; 29 -> 0xFFFFD700
    30 -> 0xFFADFF2F; 31 -> 0xFF7CFC00
    else -> 0xFF2196F3
}.toInt()

fun String.normalizeSpaces(): String = trim().replace(Regex("\\s+"), " ")
