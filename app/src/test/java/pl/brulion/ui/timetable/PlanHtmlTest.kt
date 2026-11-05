package pl.brulion.ui.timetable

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.brulion.data.db.LessonState
import pl.brulion.data.db.TimetableLessonEntity
import java.time.LocalDate

class PlanHtmlTest {
    private val monday = LocalDate.of(2026, 11, 2)

    private fun lesson(subject: String, state: LessonState = LessonState.NORMAL, day: Int = 0) = TimetableLessonEntity(
        profileId = 1,
        date = monday.plusDays(day.toLong()),
        lessonNo = 1,
        position = 0,
        startTime = "07:50",
        endTime = "08:35",
        subjectName = subject,
        teacherName = "Anna Kowalska",
        classroom = "12",
        state = state,
        originalSubjectName = null,
        originalTeacherName = null,
        otherDate = null,
        otherLessonNo = null,
    )

    @Test
    fun studentNameAndSubjectsAreEscaped() {
        val html = planHtml("Plan", monday, listOf(PrintedStudent("Ola <script>", listOf(lesson("Matma & fizyka")))))

        assertTrue(html.contains("Ola &lt;script&gt;"))
        assertTrue(html.contains("Matma &amp; fizyka"))
        assertFalse(html.contains("<script>"))
    }

    @Test
    fun changesAreMarkedInTheCell() {
        val html = planHtml(
            "Plan",
            monday,
            listOf(PrintedStudent("Ola", listOf(lesson("Chemia", LessonState.CANCELLED), lesson("Fizyka", LessonState.SUBSTITUTION, day = 1)))),
        )

        assertTrue(html.contains("odwołana"))
        assertTrue(html.contains("zastępstwo"))
    }

    @Test
    fun regularPlanHasNoChangeMarks() {
        val html = planHtml("Plan", monday, listOf(PrintedStudent("Ola", listOf(lesson("Matma")))))

        assertFalse(html.contains("odwołana"))
        assertFalse(html.contains("zastępstwo"))
        assertFalse(html.contains("przeniesiona"))
    }

    @Test
    fun eachStudentStartsOnItsOwnPage() {
        val html = planHtml(
            "Plan",
            monday,
            listOf(PrintedStudent("Ola", listOf(lesson("Matma"))), PrintedStudent("Jan", listOf(lesson("Polski")))),
        )

        assertEquals(2, Regex("<section>").findAll(html).count())
    }
}
