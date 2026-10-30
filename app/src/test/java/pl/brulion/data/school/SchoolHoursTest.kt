package pl.brulion.data.school

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.brulion.data.db.LessonState
import pl.brulion.data.db.TimetableLessonEntity
import java.time.LocalDate

class SchoolHoursTest {
    private fun lesson(no: Int, start: String, end: String, state: LessonState = LessonState.NORMAL) = TimetableLessonEntity(
        profileId = 1, date = LocalDate.of(2026, 11, 3), lessonNo = no, position = 0, startTime = start, endTime = end,
        subjectName = null, teacherName = null, classroom = null, state = state,
        originalSubjectName = null, originalTeacherName = null, otherDate = null, otherLessonNo = null,
    )

    @Test
    fun cancelledEdgesDontCount() {
        val day = listOf(
            lesson(1, "07:50", "08:35", LessonState.CANCELLED),
            lesson(2, "08:55", "09:40"),
            lesson(3, "09:50", "10:35", LessonState.SUBSTITUTION),
            lesson(4, "10:45", "11:30", LessonState.MOVED_AWAY),
        )
        assertEquals("08:55" to "10:35", schoolHours(day))
    }

    @Test
    fun nothingHeld() {
        assertNull(schoolHours(listOf(lesson(1, "07:50", "08:35", LessonState.CANCELLED))))
        assertNull(schoolHours(emptyList()))
    }
}
