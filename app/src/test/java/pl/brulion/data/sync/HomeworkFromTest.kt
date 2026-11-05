package pl.brulion.data.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class HomeworkFromTest {
    private fun item(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

    private val subjects = mapOf(7L to "Matematyka")
    private val teachers = mapOf(9L to "Anna Kowalska")

    @Test
    fun mapsDueDateSubjectTeacherAndText() {
        val homework = homeworkFrom(
            listOf(
                item(
                    """{"Id": 41, "DueDate": "2026-11-09", "Date": "2026-11-03",
                        "Subject": {"Id": 7}, "Teacher": {"Id": 9},
                        "Topic": "  Równania   kwadratowe ", "Text": " Zadania 1-5 str. 80 "}""",
                ),
            ),
            profileId = 1,
            subjects = subjects,
            teachers = teachers,
        ).single()

        assertEquals(41L, homework.id)
        assertEquals(LocalDate.of(2026, 11, 9), homework.dueDate)
        assertEquals(LocalDate.of(2026, 11, 3), homework.addedDate)
        assertEquals("Matematyka", homework.subjectName)
        assertEquals("Anna Kowalska", homework.teacherName)
        assertEquals("Równania kwadratowe", homework.topic)
        assertEquals("Zadania 1-5 str. 80", homework.content)
    }

    @Test
    fun unknownSubjectAndTeacherStayEmpty() {
        val homework = homeworkFrom(
            listOf(item("""{"Id": 1, "DueDate": "2026-11-09", "Subject": {"Id": 99}, "Topic": "Czytanie"}""")),
            profileId = 1,
            subjects = subjects,
            teachers = teachers,
        ).single()

        assertNull(homework.subjectName)
        assertNull(homework.teacherName)
        assertEquals("", homework.content)
    }

    @Test
    fun itemWithoutIdOrDueDateIsSkipped() {
        val homework = homeworkFrom(
            listOf(
                item("""{"DueDate": "2026-11-09", "Topic": "bez id"}"""),
                item("""{"Id": 2, "Topic": "bez terminu"}"""),
                item("""{"Id": 3, "DueDate": "2026-11-10", "Topic": "ok"}"""),
            ),
            profileId = 1,
            subjects = subjects,
            teachers = teachers,
        )

        assertEquals(listOf(3L), homework.map { it.id })
    }
}
