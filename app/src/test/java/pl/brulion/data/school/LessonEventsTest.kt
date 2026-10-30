package pl.brulion.data.school

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.brulion.data.db.EventEntity
import pl.brulion.data.db.LessonState
import pl.brulion.data.db.TimetableLessonEntity
import java.time.LocalDate

class LessonEventsTest {
    private val day = LocalDate.of(2026, 11, 3)

    private fun lesson(no: Int, subject: String, state: LessonState = LessonState.NORMAL) = TimetableLessonEntity(
        profileId = 1, date = day, lessonNo = no, position = 0, startTime = null, endTime = null,
        subjectName = subject, teacherName = null, classroom = null, state = state,
        originalSubjectName = null, originalTeacherName = null, otherDate = null, otherLessonNo = null,
    )

    private fun event(id: Long, no: Int?, subject: String?) = EventEntity(
        profileId = 1, id = id, date = day, lessonNo = no, startTime = null, typeName = "Sprawdzian", color = 0,
        subjectName = subject, teacherName = null, topic = "", addedAt = 0, firstSeenAt = 0,
    )

    private val plan = listOf(
        lesson(1, "Matematyka"), lesson(2, "Język polski"), lesson(3, "Język angielski"),
        lesson(4, "Religia - formacja"), lesson(5, "Matematyka"), lesson(6, "Historia", LessonState.CANCELLED),
    )

    private fun lessonsOf(vararg events: EventEntity) = eventsByLesson(plan, events.toList()).mapKeys { it.key.lessonNo }.mapValues { (_, v) -> v.map { it.id } }

    @Test
    fun subjectWinsOverAWrongNumber() {
        assertEquals(mapOf(3 to listOf(1L)), lessonsOf(event(1, 1, "Język angielski")))
    }

    @Test
    fun numberPicksBetweenLessonsOfTheSubject() {
        assertEquals(mapOf(5 to listOf(1L)), lessonsOf(event(1, 5, "Matematyka")))
        assertEquals(mapOf(1 to listOf(1L)), lessonsOf(event(1, 2, "Matematyka")))
    }

    @Test
    fun noSubjectGoesByNumber() {
        assertEquals(mapOf(2 to listOf(1L)), lessonsOf(event(1, 2, null)))
    }

    @Test
    fun subjectPrefixAndUnmatched() {
        assertEquals(mapOf(4 to listOf(1L)), lessonsOf(event(1, 4, "Religia")))
        // No biology that day, and the cancelled history lesson won't have the test.
        assertTrue(lessonsOf(event(1, 6, "Biologia"), event(2, 6, "Historia")).isEmpty())
    }
}
