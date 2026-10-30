package pl.brulion.data.school

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.brulion.data.db.EventEntity
import pl.brulion.data.db.GradeEntity
import pl.brulion.data.db.GradeKind
import pl.brulion.data.db.GradeType
import pl.brulion.data.db.LessonState
import pl.brulion.data.db.TimetableLessonEntity
import java.time.LocalDate

class TodayContentTest {
    private val today = LocalDate.of(2026, 11, 3)
    private val now = 1_800_000_000L
    private val day = 24 * 60 * 60L

    private fun lesson(date: LocalDate, end: String, state: LessonState = LessonState.NORMAL) = TimetableLessonEntity(
        profileId = 1, date = date, lessonNo = 1, position = 0, startTime = "07:50", endTime = end,
        subjectName = "Matematyka", teacherName = null, classroom = null, state = state,
        originalSubjectName = null, originalTeacherName = null, otherDate = null, otherLessonNo = null,
    )

    private fun grade(id: Long, addedAgo: Long, seenAgo: Long? = null) = GradeEntity(
        profileId = 1, kind = GradeKind.NORMAL, id = id, type = GradeType.REGULAR, semester = 1, subjectId = 1,
        subjectName = "M", value = "5", numericValue = 5.0, maxValue = null, weight = 1.0, categoryName = "",
        color = 0, comment = null, teacherName = null, addedAt = now - addedAgo, improvedById = null,
        firstSeenAt = seenAgo?.let { now - it } ?: 0,
    )

    private fun event(date: LocalDate) = EventEntity(
        profileId = 1, id = date.toEpochDay(), date = date, lessonNo = null, startTime = null, typeName = "Sprawdzian",
        color = 0, subjectName = null, teacherName = null, topic = "", addedAt = 0, firstSeenAt = 0,
    )

    private fun content(
        lessons: List<TimetableLessonEntity> = emptyList(),
        grades: List<GradeEntity> = emptyList(),
        events: List<EventEntity> = emptyList(),
        nowTime: String = "12:00",
    ) = todayContent(lessons, emptyList(), grades, events, emptyList(), today, now, nowTime)

    @Test
    fun lessonsOverTodayMoveToTheNextDay() {
        val lessons = listOf(lesson(today, "11:30"), lesson(today.plusDays(1), "12:35"))
        assertEquals(today.plusDays(1), content(lessons, nowTime = "12:00").lessonDay?.first)
        assertEquals(today, content(lessons, nowTime = "10:00").lessonDay?.first)
    }

    @Test
    fun recentGradesIncludeOldOnesJustSeen() {
        val grades = listOf(grade(1, addedAgo = 2 * day), grade(2, addedAgo = 30 * day, seenAgo = 3600), grade(3, addedAgo = 30 * day))
        assertEquals(listOf(1L, 2L), content(grades = grades).recentGrades.map { it.id })
    }

    @Test
    fun weekOfPlannerAndQuietness() {
        val events = listOf(event(today.minusDays(1)), event(today), event(today.plusDays(7)), event(today.plusDays(8)))
        assertEquals(listOf(today, today.plusDays(7)), content(events = events).upcoming.map { it.date })
        // A cancelled lesson tomorrow matters to the parent; lessons alone only to the student.
        val quietForParent = content(lessons = listOf(lesson(today.plusDays(1), "12:35")))
        assertTrue(quietForParent.isQuiet(isParent = true))
        assertFalse(quietForParent.isQuiet(isParent = false))
        assertFalse(content(lessons = listOf(lesson(today.plusDays(1), "12:35", LessonState.CANCELLED))).isQuiet(isParent = true))
    }
}
