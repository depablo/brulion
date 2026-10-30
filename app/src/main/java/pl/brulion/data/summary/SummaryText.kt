package pl.brulion.data.summary

import pl.brulion.data.db.AnnouncementEntity
import pl.brulion.data.db.AttendanceCategory
import pl.brulion.data.db.AttendanceEntity
import pl.brulion.data.db.EventEntity
import pl.brulion.data.db.GradeEntity
import pl.brulion.data.db.GradeType
import pl.brulion.data.db.LessonState
import pl.brulion.data.db.LuckyNumberEntity
import pl.brulion.data.db.MessageEntity
import pl.brulion.data.db.NoteEntity
import pl.brulion.data.db.NotePolarity
import pl.brulion.data.db.TimetableLessonEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Everything the evening summary of one child is made of. */
data class SummaryInput(
    val studentName: String,
    val newGrades: List<GradeEntity>,
    val newNotes: List<NoteEntity>,
    /** New attendance entries other than plain presence. */
    val newAbsences: List<AttendanceEntity>,
    val newAnnouncements: List<AnnouncementEntity>,
    /** All unread messages, not only new ones: they still need attention. */
    val unreadMessages: List<MessageEntity>,
    /** New calendar entries that aren't on [nextDay] (those are listed anyway). */
    val newEvents: List<EventEntity>,
    /** The next school day, or null when the timetable doesn't reach it. */
    val nextDay: LocalDate?,
    val nextDayEvents: List<EventEntity>,
    /** Cancelled, substituted or moved lessons on [nextDay]. */
    val nextDayChanges: List<TimetableLessonEntity>,
    val nextDayLuckyNumber: LuckyNumberEntity?,
)

/** [warning] says the data couldn't be refreshed; then an empty summary isn't a quiet day. */
data class DailySummary(val title: String, val lines: List<String>, val warning: String? = null) {
    val isEmpty: Boolean get() = lines.isEmpty()
    val text: String get() = when {
        warning != null -> (listOf(warning) + lines).joinToString("\n")
        isEmpty -> QUIET_TEXT
        else -> lines.joinToString("\n")
    }
    /** The first line, for the collapsed notification. */
    val headline: String get() = warning ?: lines.firstOrNull() ?: QUIET_TEXT

    companion object {
        const val QUIET_TEXT = "Spokojny dzień, nic nowego w Librusie."
    }
}

fun buildSummary(input: SummaryInput, today: LocalDate): DailySummary {
    val lines = buildList {
        gradesLine(input.newGrades)?.let(::add)
        notesLine(input.newNotes)?.let(::add)
        absencesLine(input.newAbsences)?.let(::add)
        messagesLine(input.unreadMessages)?.let(::add)
        announcementsLine(input.newAnnouncements)?.let(::add)
        input.nextDay?.let { day ->
            val label = dayLabel(day, today)
            if (input.nextDayEvents.isNotEmpty()) {
                add("$label: " + input.nextDayEvents.joinToString(", ") { it.shortTitle() } + ".")
            }
            changesLine(label, input.nextDayChanges)?.let(::add)
            input.nextDayLuckyNumber?.takeIf { it.date == day }?.let {
                add("Szczęśliwy numerek (${label.lowercase()}): ${it.number}.")
            }
        }
        newEventsLine(input.newEvents)?.let(::add)
    }
    return DailySummary("${input.studentName.substringBefore(' ')}: podsumowanie dnia", lines)
}

private fun gradesLine(grades: List<GradeEntity>): String? {
    if (grades.isEmpty()) return null
    val bySubject = grades.sortedBy { it.addedAt }.groupBy { it.subjectName }.entries.joinToString("; ") { (subject, list) ->
        subject + " " + list.joinToString(", ") { grade ->
            val kind = grade.type.summaryLabel()
            listOfNotNull(kind, grade.value).joinToString(" ") +
                grade.categoryName.takeIf { it.isNotBlank() && kind == null }?.let { " ($it)" }.orEmpty()
        }
    }
    return (if (grades.size == 1) "Nowa ocena: " else "Nowe oceny: ") + bySubject + "."
}

private fun GradeType.summaryLabel(): String? = when (this) {
    GradeType.REGULAR -> null
    GradeType.SEMESTER_PROPOSED -> "przewidywana śródroczna"
    GradeType.SEMESTER_FINAL -> "śródroczna"
    GradeType.YEAR_PROPOSED -> "przewidywana roczna"
    GradeType.YEAR_FINAL -> "roczna"
}

private fun notesLine(notes: List<NoteEntity>): String? {
    if (notes.isEmpty()) return null
    if (notes.size == 1) {
        val note = notes.single()
        val polarity = when (note.polarity) {
            NotePolarity.POSITIVE -> " pozytywna"
            NotePolarity.NEGATIVE -> " negatywna"
            NotePolarity.NEUTRAL -> ""
        }
        return "Nowa uwaga$polarity: „${note.text.ellipsize(120)}”."
    }
    val positive = notes.count { it.polarity == NotePolarity.POSITIVE }
    val negative = notes.count { it.polarity == NotePolarity.NEGATIVE }
    val details = listOfNotNull(
        positive.takeIf { it > 0 }?.let { "$it ${polishPlural(it, "pozytywna", "pozytywne", "pozytywnych")}" },
        negative.takeIf { it > 0 }?.let { "$it ${polishPlural(it, "negatywna", "negatywne", "negatywnych")}" },
    ).joinToString(", ")
    return "${notes.size} ${polishPlural(notes.size, "nowa uwaga", "nowe uwagi", "nowych uwag")}" +
        (if (details.isNotEmpty()) " ($details)" else "") + "."
}

private fun absencesLine(entries: List<AttendanceEntity>): String? {
    if (entries.isEmpty()) return null
    val counts = entries.groupingBy { it.category }.eachCount()
    val parts = listOfNotNull(
        counts[AttendanceCategory.ABSENT]?.let { "$it ${polishPlural(it, "nieobecność", "nieobecności", "nieobecności")}" },
        counts[AttendanceCategory.LATE]?.let { "$it ${polishPlural(it, "spóźnienie", "spóźnienia", "spóźnień")}" },
        counts[AttendanceCategory.EXCUSED]?.let {
            "$it ${polishPlural(it, "usprawiedliwiona nieobecność", "usprawiedliwione nieobecności", "usprawiedliwionych nieobecności")}"
        },
        counts[AttendanceCategory.RELEASED]?.let { "$it ${polishPlural(it, "zwolnienie", "zwolnienia", "zwolnień")}" },
        counts[AttendanceCategory.OTHER]?.let { "$it ${polishPlural(it, "inny wpis", "inne wpisy", "innych wpisów")}" },
    )
    return "Frekwencja: ${parts.joinToString(", ")}."
}

private fun messagesLine(unread: List<MessageEntity>): String? {
    if (unread.isEmpty()) return null
    if (unread.size == 1) {
        val message = unread.single()
        return "Nieprzeczytana wiadomość od ${message.senderName}: „${message.subject.ellipsize(80)}”."
    }
    val senders = unread.sortedByDescending { it.sentAt }.map { it.senderName }.distinct()
    val shown = senders.take(3).joinToString(", ") + if (senders.size > 3) " i innych" else ""
    return "${unread.size} ${polishPlural(unread.size, "nieprzeczytana wiadomość", "nieprzeczytane wiadomości", "nieprzeczytanych wiadomości")}, m.in. od: $shown."
}

private fun announcementsLine(announcements: List<AnnouncementEntity>): String? {
    if (announcements.isEmpty()) return null
    val titles = announcements.take(3).joinToString(", ") { "„${it.subject.ellipsize(60)}”" }
    if (announcements.size == 1) return "Nowe ogłoszenie: $titles."
    return "${announcements.size} ${polishPlural(announcements.size, "nowe ogłoszenie", "nowe ogłoszenia", "nowych ogłoszeń")}: $titles" +
        (if (announcements.size > 3) " i inne." else ".")
}

private fun changesLine(dayLabel: String, lessons: List<TimetableLessonEntity>): String? {
    if (lessons.isEmpty()) return null
    val parts = lessons.sortedBy { it.lessonNo }.map { lesson ->
        val subject = lesson.subjectName ?: lesson.originalSubjectName
        val what = when (lesson.state) {
            LessonState.CANCELLED -> "odwołana"
            LessonState.SUBSTITUTION -> "zastępstwo na"
            LessonState.MOVED_AWAY -> "przeniesiona"
            LessonState.MOVED_HERE -> "przeniesiona na"
            LessonState.NORMAL -> ""
        }
        "$what ${lesson.lessonNo}. lekcja" + (subject?.let { " ($it)" } ?: "")
    }
    return "Zmiany w planie (${dayLabel.lowercase()}): ${parts.joinToString(", ")}."
}

private fun newEventsLine(events: List<EventEntity>): String? {
    if (events.isEmpty()) return null
    val shown = events.sortedBy { it.date }.take(3).joinToString(", ") { "${it.shortTitle()} (${it.date.format(ShortDate)})" }
    return "Nowe w terminarzu: $shown" + (if (events.size > 3) " i więcej." else ".")
}

private val ShortDate = DateTimeFormatter.ofPattern("d.MM")

/** "Sprawdzian – Matematyka (ułamki)". */
private fun EventEntity.shortTitle(): String =
    listOfNotNull(typeName, subjectName).joinToString(" – ") +
        topic.trimEnd('.', ' ').takeIf { it.isNotBlank() }?.let { " (${it.ellipsize(50)})" }.orEmpty()

/** "Jutro", "W poniedziałek", "We wtorek"; a date when it's further than a week away. */
internal fun dayLabel(day: LocalDate, today: LocalDate): String = when {
    day == today.plusDays(1) -> "Jutro"
    day.isAfter(today.plusDays(6)) -> day.format(ShortDate)
    else -> when (day.dayOfWeek!!) {
        DayOfWeek.MONDAY -> "W poniedziałek"
        DayOfWeek.TUESDAY -> "We wtorek"
        DayOfWeek.WEDNESDAY -> "W środę"
        DayOfWeek.THURSDAY -> "W czwartek"
        DayOfWeek.FRIDAY -> "W piątek"
        DayOfWeek.SATURDAY -> "W sobotę"
        DayOfWeek.SUNDAY -> "W niedzielę"
    }
}

/** Polish plural forms: 1 ocena, 2-4 oceny (but 12-14 ocen), 5+ ocen. */
internal fun polishPlural(count: Int, one: String, few: String, many: String): String = when {
    count == 1 -> one
    count % 10 in 2..4 && count % 100 !in 12..14 -> few
    else -> many
}

private fun String.ellipsize(max: Int): String {
    val flat = replace(Regex("\\s+"), " ").trim()
    return if (flat.length <= max) flat else flat.take(max - 1).trimEnd() + "…"
}
