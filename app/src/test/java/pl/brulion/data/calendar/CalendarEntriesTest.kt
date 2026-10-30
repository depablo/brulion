package pl.brulion.data.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.brulion.data.db.EventEntity
import pl.brulion.data.db.LessonState
import pl.brulion.data.db.ProfileEntity
import pl.brulion.data.db.TimetableLessonEntity
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class CalendarEntriesTest {
    private val zone = ZoneId.of("Europe/Warsaw")
    private val today = LocalDate.of(2026, 11, 2)
    private val day = LocalDate.of(2026, 11, 3)

    private val profiles = listOf(
        ProfileEntity(id = 1, accountId = 1, synergiaLogin = "111", studentName = "Anna Nowak", isParent = true),
        ProfileEntity(id = 2, accountId = 1, synergiaLogin = "111u", studentName = "Anna Nowak", isParent = false),
        ProfileEntity(id = 3, accountId = 1, synergiaLogin = "222", studentName = "Ola Nowak", isParent = true),
    )

    private fun event(profileId: Long, id: Long, date: LocalDate = day, subject: String? = "Matematyka") = EventEntity(
        profileId = profileId, id = id, date = date, lessonNo = 1, startTime = "07:50", typeName = "Sprawdzian", color = 0,
        subjectName = subject, teacherName = "Jan Kowalski", topic = "Ułamki", addedAt = 0, firstSeenAt = 0,
    )

    private val lesson = TimetableLessonEntity(
        profileId = 2, date = day, lessonNo = 3, position = 0, startTime = "09:50", endTime = "10:35",
        subjectName = "Matematyka", teacherName = null, classroom = null, state = LessonState.NORMAL,
        originalSubjectName = null, originalTeacherName = null, otherDate = null, otherLessonNo = null,
    )

    @Test
    fun oncePerChildAtTheMatchedLesson() {
        // The same test on Anna's parent and student profile; only the student one has the timetable.
        val entries = calendarEntries(profiles, listOf(event(1, 10), event(2, 10), event(3, 20)), listOf(lesson), today, zone)
        assertEquals(2, entries.size)
        val anna = entries.single { it.title.endsWith("Anna") }
        assertEquals("Sprawdzian – Matematyka · Anna", anna.title)
        assertEquals(ZonedDateTime.of(day.atTime(9, 50), zone).toInstant().toEpochMilli(), anna.start)
        assertEquals(false, anna.allDay)
        assertTrue(anna.description.startsWith("Ułamki\nJan Kowalski"))
        // Ola has no timetable here, so no guessing the hour from the planner's lesson number.
        assertEquals(true, entries.single { it.title.endsWith("Ola") }.allDay)
    }

    @Test
    fun pastEntriesAreLeftOut() {
        assertTrue(calendarEntries(profiles, listOf(event(1, 10, date = today.minusDays(1))), emptyList(), today, zone).isEmpty())
    }
}
