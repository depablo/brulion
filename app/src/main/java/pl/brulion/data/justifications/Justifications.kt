/*
 * Part of Brulion, GNU GPL v3. Based on Librus support in Szkolny.eu
 * (https://github.com/szkolny-eu/szkolny-android), (c) Kuba Szczodrzyński and contributors, GPL v3.
 */
package pl.brulion.data.justifications

import pl.brulion.data.db.AttendanceCategory
import pl.brulion.data.db.AttendanceEntity
import pl.brulion.librus.LibrusJustifications
import java.time.LocalDate

/** The two ways the form can cover absences: chosen lessons on one day, or whole days in a range. */
enum class JustificationMode { LESSONS, DAYS }

/** Attendance categories a justification can cover. */
val JUSTIFIABLE_CATEGORIES = setOf(AttendanceCategory.ABSENT, AttendanceCategory.LATE)

/** Lessons on [date] that a justification can cover: absences and late arrivals, earliest first. */
fun justifiableEntries(entries: List<AttendanceEntity>, date: LocalDate): List<AttendanceEntity> =
    entries
        .filter { it.date == date && it.lessonNo != null && it.category in JUSTIFIABLE_CATEGORIES }
        .sortedBy { it.lessonNo }

/** Librus only offers to notify teachers about absences that are today or still ahead. */
fun canNotifyTeachers(dateTo: LocalDate, today: LocalDate): Boolean = !dateTo.isBefore(today)

/**
 * The request for the form's current state, or null while the form is incomplete.
 * Lessons mode always covers the single [date]; days mode covers [from]..[to].
 */
fun justificationRequest(
    mode: JustificationMode,
    date: LocalDate,
    from: LocalDate,
    to: LocalDate,
    lessons: Set<Int>,
    message: String,
    notifyTeachers: Boolean,
    today: LocalDate,
): LibrusJustifications.Request? {
    val (dateFrom, dateTo, lessonNumbers) = when (mode) {
        JustificationMode.LESSONS -> {
            if (lessons.isEmpty()) return null
            Triple(date, date, lessons.sorted())
        }
        JustificationMode.DAYS -> {
            if (to.isBefore(from)) return null
            Triple(from, to, emptyList())
        }
    }
    return LibrusJustifications.Request(
        dateFrom = dateFrom,
        dateTo = dateTo,
        lessons = lessonNumbers,
        message = message.trim(),
        notifyTeachers = notifyTeachers && canNotifyTeachers(dateTo, today),
    )
}
