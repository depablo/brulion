package pl.brulion.ui.timetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.brulion.data.db.LessonState
import pl.brulion.data.db.TimetableLessonEntity
import java.time.DayOfWeek
import java.time.LocalDate

class WeekGridTest {
    private val monday = LocalDate.of(2026, 11, 2)

    private fun lesson(
        day: Int,
        lessonNo: Int,
        subject: String?,
        position: Int = 0,
        start: String? = null,
        state: LessonState = LessonState.NORMAL,
    ) = TimetableLessonEntity(
        profileId = 1,
        date = monday.plusDays(day.toLong()),
        lessonNo = lessonNo,
        position = position,
        startTime = start,
        endTime = null,
        subjectName = subject,
        teacherName = null,
        classroom = null,
        state = state,
        originalSubjectName = null,
        originalTeacherName = null,
        otherDate = null,
        otherLessonNo = null,
    )

    @Test
    fun rowsAreLessonNumbersInOrder() {
        val grid = weekGrid(listOf(lesson(0, 3, "Mat"), lesson(1, 1, "Pol"), lesson(2, 3, "Fiz")))
        assertEquals(listOf(1, 3), grid.lessonNumbers)
    }

    @Test
    fun startTimeComesFromTheFirstLessonThatHasOne() {
        val grid = weekGrid(listOf(lesson(0, 2, "Mat", start = null), lesson(1, 2, "Pol", start = "09:00")))
        assertEquals("09:00", grid.startTime(2))
    }

    @Test
    fun cellHoldsSharedSlotLessonsInPositionOrder() {
        val grid = weekGrid(listOf(lesson(0, 1, "Grupa B", position = 1), lesson(0, 1, "Grupa A", position = 0)))
        assertEquals(listOf("Grupa A", "Grupa B"), grid.at(monday, 1).map { it.subjectName })
    }

    @Test
    fun emptyCellHasNoLessons() {
        val grid = weekGrid(listOf(lesson(0, 1, "Mat")))
        assertTrue(grid.at(monday.plusDays(1), 1).isEmpty())
    }

    @Test
    fun weekendOnlyWhenSomethingIsPlanned() {
        val weekdaysOnly = weekDays(monday, listOf(lesson(0, 1, "Mat")))
        assertEquals(5, weekdaysOnly.size)
        assertEquals(DayOfWeek.FRIDAY, weekdaysOnly.last().dayOfWeek)

        val withSaturday = weekDays(monday, listOf(lesson(5, 1, "Zajęcia dodatkowe")))
        assertEquals(6, withSaturday.size)
        assertEquals(DayOfWeek.SATURDAY, withSaturday.last().dayOfWeek)
    }
}
