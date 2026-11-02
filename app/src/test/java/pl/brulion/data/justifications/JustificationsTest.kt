package pl.brulion.data.justifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.brulion.data.db.AttendanceCategory
import pl.brulion.data.db.AttendanceEntity
import java.time.LocalDate

class JustificationsTest {
    private val today = LocalDate.of(2026, 10, 12)
    private val yesterday = today.minusDays(1)

    private fun entry(id: String, date: LocalDate, lessonNo: Int?, category: AttendanceCategory) = AttendanceEntity(
        profileId = 1,
        id = id,
        date = date,
        lessonNo = lessonNo,
        semester = 1,
        category = category,
        typeName = "type $id",
        typeShort = "x",
        typeColor = null,
        subjectName = "Subject $id",
        teacherName = null,
        addedAt = 0,
        firstSeenAt = 0,
    )

    @Test
    fun onlyAbsencesAndLatesOfTheDayAreJustifiable() {
        val entries = listOf(
            entry("a", today, 5, AttendanceCategory.ABSENT),
            entry("b", today, 2, AttendanceCategory.LATE),
            entry("c", today, 3, AttendanceCategory.PRESENT),
            entry("d", today, 4, AttendanceCategory.EXCUSED),
            entry("e", today, null, AttendanceCategory.ABSENT),
            entry("f", yesterday, 1, AttendanceCategory.ABSENT),
        )

        assertEquals(listOf("b", "a"), justifiableEntries(entries, today).map { it.id })
    }

    @Test
    fun lessonsModeNeedsAtLeastOneLesson() {
        assertNull(request(JustificationMode.LESSONS, lessons = emptySet()))
    }

    @Test
    fun lessonsModeCoversOneDayAndSortsLessons() {
        val request = request(JustificationMode.LESSONS, lessons = setOf(5, 2))!!

        assertEquals(today, request.dateFrom)
        assertEquals(today, request.dateTo)
        assertEquals(listOf(2, 5), request.lessons)
    }

    @Test
    fun daysModeRejectsEndBeforeStart() {
        assertNull(request(JustificationMode.DAYS, from = today, to = yesterday))
    }

    @Test
    fun daysModeHasNoLessons() {
        val request = request(JustificationMode.DAYS, from = yesterday, to = today, lessons = setOf(3))!!

        assertEquals(yesterday, request.dateFrom)
        assertEquals(today, request.dateTo)
        assertEquals(emptyList<Int>(), request.lessons)
    }

    @Test
    fun messageIsTrimmed() {
        val request = request(JustificationMode.LESSONS, lessons = setOf(1), message = "  Proszę.  ")!!

        assertEquals("Proszę.", request.message)
    }

    @Test
    fun teachersAreNotifiedOnlyForAbsencesThatAreNotOver() {
        assertTrue(canNotifyTeachers(dateTo = today, today = today))
        assertFalse(canNotifyTeachers(dateTo = yesterday, today = today))

        val past = request(JustificationMode.DAYS, from = yesterday, to = yesterday, notify = true)!!
        assertFalse(past.notifyTeachers)
        val current = request(JustificationMode.DAYS, from = yesterday, to = today, notify = true)!!
        assertTrue(current.notifyTeachers)
    }

    private fun request(
        mode: JustificationMode,
        from: LocalDate = today,
        to: LocalDate = today,
        lessons: Set<Int> = emptySet(),
        message: String = "Proszę.",
        notify: Boolean = false,
    ) = justificationRequest(mode, date = today, from = from, to = to, lessons = lessons, message = message, notifyTeachers = notify, today = today)
}
