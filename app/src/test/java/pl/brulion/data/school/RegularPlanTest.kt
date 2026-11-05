package pl.brulion.data.school

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.brulion.data.db.LessonState
import pl.brulion.data.db.TimetableLessonEntity
import java.time.LocalDate

class RegularPlanTest {
    private val day = LocalDate.of(2026, 11, 2)

    private fun lesson(
        no: Int,
        state: LessonState,
        subject: String,
        original: String? = null,
    ) = TimetableLessonEntity(
        profileId = 1,
        date = day,
        lessonNo = no,
        position = 0,
        startTime = "08:00",
        endTime = "08:45",
        subjectName = subject,
        teacherName = "Nowa",
        classroom = null,
        state = state,
        originalSubjectName = original,
        originalTeacherName = original?.let { "Stary" },
        otherDate = null,
        otherLessonNo = null,
    )

    @Test
    fun substitutionShowsThePlannedLesson() {
        val result = regularPlan(listOf(lesson(1, LessonState.SUBSTITUTION, "Zastępstwo", original = "Matematyka"))).single()

        assertEquals(LessonState.NORMAL, result.state)
        assertEquals("Matematyka", result.subjectName)
        assertEquals("Stary", result.teacherName)
    }

    @Test
    fun lessonMovedInFromAnotherDayIsLeftOut() {
        assertEquals(emptyList<TimetableLessonEntity>(), regularPlan(listOf(lesson(2, LessonState.MOVED_HERE, "Fizyka"))))
    }

    @Test
    fun cancelledAndMovedAwayLessonsAreRegular() {
        val result = regularPlan(
            listOf(lesson(3, LessonState.CANCELLED, "Chemia"), lesson(4, LessonState.MOVED_AWAY, "Historia")),
        )

        assertEquals(listOf(LessonState.NORMAL, LessonState.NORMAL), result.map { it.state })
        assertEquals(listOf("Chemia", "Historia"), result.map { it.subjectName })
    }
}
