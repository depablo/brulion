package pl.brulion.data.summary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.brulion.data.db.AttendanceCategory
import pl.brulion.data.db.AttendanceEntity
import pl.brulion.data.db.EventEntity
import pl.brulion.data.db.GradeEntity
import pl.brulion.data.db.GradeKind
import pl.brulion.data.db.GradeType
import pl.brulion.data.db.LessonState
import pl.brulion.data.db.MessageEntity
import pl.brulion.data.db.TimetableLessonEntity
import java.time.LocalDate

class SummaryTextTest {
    private val wednesday = LocalDate.of(2026, 10, 28)

    private fun empty() = SummaryInput(
        studentName = "Jan Kowalski", newGrades = emptyList(), newNotes = emptyList(), newAbsences = emptyList(),
        newAnnouncements = emptyList(), unreadMessages = emptyList(), newEvents = emptyList(), nextDay = null,
        nextDayEvents = emptyList(), nextDayChanges = emptyList(), nextDayLuckyNumber = null,
    )

    private fun grade(subject: String, value: String, category: String = "", type: GradeType = GradeType.REGULAR) = GradeEntity(
        profileId = 1, kind = GradeKind.NORMAL, id = value.hashCode().toLong() + subject.hashCode(), type = type,
        semester = 1, subjectId = 1, subjectName = subject, value = value, numericValue = null, maxValue = null,
        weight = 1.0, categoryName = category, color = 0, comment = null, teacherName = null, addedAt = 0,
        improvedById = null, firstSeenAt = 1,
    )

    private fun message(id: Long, sender: String) = MessageEntity(
        profileId = 1, id = id, senderName = sender, subject = "Temat $id", preview = "", sentAt = id,
        readAt = null, hasAttachments = false, firstSeenAt = 1,
    )

    @Test
    fun quietDay() {
        val summary = buildSummary(empty(), wednesday)
        assertTrue(summary.isEmpty)
        assertEquals("Jan: podsumowanie dnia", summary.title)
        assertEquals(DailySummary.QUIET_TEXT, summary.text)
    }

    @Test
    fun gradesGroupedBySubject() {
        val input = empty().copy(
            newGrades = listOf(
                grade("Matematyka", "5", "sprawdzian"),
                grade("Matematyka", "4+"),
                grade("Historia", "4", type = GradeType.SEMESTER_PROPOSED),
            )
        )
        assertEquals(
            "Nowe oceny: Matematyka 5 (sprawdzian), 4+; Historia przewidywana śródroczna 4.",
            buildSummary(input, wednesday).lines.single(),
        )
    }

    @Test
    fun messagesAndPlurals() {
        val one = buildSummary(empty().copy(unreadMessages = listOf(message(1, "Anna Nowak"))), wednesday)
        assertEquals("Nieprzeczytana wiadomość od Anna Nowak: „Temat 1”.", one.lines.single())

        val many = buildSummary(
            empty().copy(unreadMessages = (1L..5L).map { message(it, "Nauczyciel $it") }),
            wednesday,
        )
        assertEquals(
            "5 nieprzeczytanych wiadomości, m.in. od: Nauczyciel 5, Nauczyciel 4, Nauczyciel 3 i innych.",
            many.lines.single(),
        )
    }

    @Test
    fun nextDayEventsAndChanges() {
        val thursday = wednesday.plusDays(1)
        val event = EventEntity(
            profileId = 1, id = 1, date = thursday, lessonNo = 2, startTime = null, typeName = "Sprawdzian",
            color = 0, subjectName = "Fizyka", teacherName = null, topic = "Ruch.", addedAt = 0, firstSeenAt = 1,
        )
        val cancelled = TimetableLessonEntity(
            profileId = 1, date = thursday, lessonNo = 6, position = 0, startTime = null, endTime = null,
            subjectName = "Religia", teacherName = null, classroom = null, state = LessonState.CANCELLED,
            originalSubjectName = null, originalTeacherName = null, otherDate = null, otherLessonNo = null,
        )
        val lines = buildSummary(
            empty().copy(nextDay = thursday, nextDayEvents = listOf(event), nextDayChanges = listOf(cancelled)),
            wednesday,
        ).lines
        assertEquals(listOf("Jutro: Sprawdzian – Fizyka (Ruch).", "Zmiany w planie (jutro): odwołana 6. lekcja (Religia)."), lines)
    }

    @Test
    fun absencesCounted() {
        fun entry(id: String, category: AttendanceCategory) = AttendanceEntity(
            profileId = 1, id = id, date = wednesday, lessonNo = 1, semester = 1, category = category, typeName = "",
            typeShort = "", typeColor = null, subjectName = null, teacherName = null, addedAt = 0, firstSeenAt = 1,
        )
        val input = empty().copy(
            newAbsences = listOf(entry("1", AttendanceCategory.ABSENT), entry("2", AttendanceCategory.ABSENT), entry("3", AttendanceCategory.LATE))
        )
        assertEquals("Frekwencja: 2 nieobecności, 1 spóźnienie.", buildSummary(input, wednesday).lines.single())
    }

    @Test
    fun dayLabelsAndPlurals() {
        assertEquals("Jutro", dayLabel(wednesday.plusDays(1), wednesday))
        assertEquals("W poniedziałek", dayLabel(LocalDate.of(2026, 11, 2), wednesday))
        assertEquals("We wtorek", dayLabel(LocalDate.of(2026, 11, 3), wednesday))
        assertEquals("12.11", dayLabel(LocalDate.of(2026, 11, 12), wednesday))
        assertEquals("ocen", polishPlural(12, "ocena", "oceny", "ocen"))
        assertEquals("oceny", polishPlural(22, "ocena", "oceny", "ocen"))
        assertEquals("ocen", polishPlural(0, "ocena", "oceny", "ocen"))
    }
}
