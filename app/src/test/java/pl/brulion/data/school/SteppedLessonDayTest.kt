package pl.brulion.data.school

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.brulion.data.db.LessonState
import pl.brulion.data.db.TimetableLessonEntity
import java.time.LocalDate

class SteppedLessonDayTest {
    private val monday = LocalDate.of(2026, 11, 2)

    private fun lesson(date: LocalDate, end: String = "08:35") = TimetableLessonEntity(
        profileId = 1, date = date, lessonNo = 1, position = 0, startTime = "07:50", endTime = end,
        subjectName = null, teacherName = null, classroom = null, state = LessonState.NORMAL,
        originalSubjectName = null, originalTeacherName = null, otherDate = null, otherLessonNo = null,
    )

    private val week = (0L..4L).map { lesson(monday.plusDays(it)) }

    @Test
    fun noStepIsTheNextLessonDay() {
        val result = steppedLessonDay(week, 0, today = monday.plusDays(1), now = "07:00")!!
        assertEquals(monday.plusDays(1), result.day)
        assertTrue(result.hasPrevious)
        assertTrue(result.hasNext)
    }

    @Test
    fun stepsBothWaysAndSkipsDaysWithoutLessons() {
        val gap = listOf(lesson(monday), lesson(monday.plusDays(3)), lesson(monday.plusDays(7)))
        assertEquals(monday.plusDays(7), steppedLessonDay(gap, 1, today = monday.plusDays(1), now = "07:00")!!.day)
        assertEquals(monday, steppedLessonDay(gap, -1, today = monday.plusDays(1), now = "07:00")!!.day)
    }

    @Test
    fun stepIsClampedAtTheEdges() {
        val last = steppedLessonDay(week, 10, today = monday, now = "07:00")!!
        assertEquals(monday.plusDays(4), last.day)
        assertEquals(4, last.step)
        assertFalse(last.hasNext)
        val first = steppedLessonDay(week, -3, today = monday, now = "07:00")!!
        assertEquals(monday, first.day)
        assertEquals(0, first.step)
        assertFalse(first.hasPrevious)
    }

    @Test
    fun afterTheLastLessonsTheDefaultIsTheLastDay() {
        val result = steppedLessonDay(week, -1, today = monday.plusDays(4), now = "15:00")!!
        assertEquals(monday.plusDays(3), result.day)
    }

    @Test
    fun noLessons() {
        assertNull(steppedLessonDay(emptyList(), 0))
    }
}
