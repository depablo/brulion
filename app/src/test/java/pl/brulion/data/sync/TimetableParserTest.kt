package pl.brulion.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.brulion.data.db.LessonState
import pl.brulion.data.db.TimetableLessonEntity
import java.time.LocalDate

class TimetableParserTest {
    private val responses = mapOf(
        "Subjects" to json("""{"Subjects":[{"Id":1,"Name":"Matematyka"},{"Id":2,"Name":"Język  polski"},{"Id":3,"Name":"Historia"},{"Id":4,"Name":"Język angielski"},{"Id":5,"Name":"Język niemiecki"}]}"""),
        "Users" to json("""{"Users":[{"Id":10,"FirstName":"Anna","LastName":"Kowalska"},{"Id":11,"FirstName":"Jan","LastName":"Nowak"},{"Id":12,"FirstName":"Ewa","LastName":"Zając"}]}"""),
        "Classrooms" to json("""{"Classrooms":[{"Id":5,"Name":"Sala 7","Symbol":"7"}]}"""),
        "Timetables?weekStart=2026-11-02" to resourceJson("/api/timetable_week.json"),
    )

    private fun parse(failing: Set<String> = emptySet()): Map<Pair<LocalDate, Int>, List<TimetableLessonEntity>> =
        withSession(responses, failing) { TimetableParser(it, profileId = 1).parseWeek(LocalDate.of(2026, 11, 2)) }
            .groupBy { it.date to it.lessonNo }

    private val monday = LocalDate.of(2026, 11, 2)
    private val wednesday = LocalDate.of(2026, 11, 4)

    @Test
    fun normalLessonWithNames() {
        val lesson = parse().getValue(monday to 1).single()
        assertEquals(LessonState.NORMAL, lesson.state)
        assertEquals("Matematyka", lesson.subjectName)
        assertEquals("Anna Kowalska", lesson.teacherName)
        assertEquals("Sala 7", lesson.classroom)
        assertEquals("07:50" to "08:35", lesson.startTime to lesson.endTime)
    }

    @Test
    fun cancelledAndSubstitution() {
        val lessons = parse()
        assertEquals(LessonState.CANCELLED, lessons.getValue(monday to 2).single().state)
        val substitution = lessons.getValue(monday to 3).single()
        assertEquals(LessonState.SUBSTITUTION, substitution.state)
        assertEquals("Matematyka" to "Ewa Zając", substitution.subjectName to substitution.teacherName)
        assertEquals("Język polski" to "Jan Nowak", substitution.originalSubjectName to substitution.originalTeacherName)
    }

    @Test
    fun moveHasBothEnds() {
        val lessons = parse()
        val away = lessons.getValue(monday to 4).single()
        assertEquals(LessonState.MOVED_AWAY, away.state)
        assertEquals(wednesday to 6, away.otherDate to away.otherLessonNo)
        val here = lessons.getValue(wednesday to 6).single()
        assertEquals(LessonState.MOVED_HERE, here.state)
        assertEquals(monday to 4, here.otherDate to here.otherLessonNo)
        // Only substitutions keep the original subject and teacher.
        assertEquals(null, here.originalSubjectName)
    }

    @Test
    fun groupsShareASlot() {
        val slot = parse().getValue(wednesday to 7)
        assertEquals(listOf(0 to "Język angielski", 1 to "Język niemiecki"), slot.map { it.position to it.subjectName })
    }

    @Test
    fun countsLessonsWithoutNumber() {
        val responses = responses + ("Timetables?weekStart=2026-11-02" to json("""{"Timetable":{"2026-11-02":[[{"HourFrom":"07:50"}],[{"LessonNo":"2","Subject":{"Id":1}}]]}}"""))
        val skipped = withSession(responses) { session ->
            TimetableParser(session, profileId = 1).run { parseWeek(LocalDate.of(2026, 11, 2)); skipped }
        }
        assertEquals(1, skipped)
    }

    @Test
    fun worksWithoutClassrooms() {
        val lessons = parse(failing = setOf("Classrooms"))
        assertEquals(null, lessons.getValue(monday to 1).single().classroom)
        assertEquals(7, lessons.values.sumOf { it.size })
    }
}
