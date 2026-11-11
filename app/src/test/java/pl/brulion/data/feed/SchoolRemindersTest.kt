package pl.brulion.data.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.brulion.data.db.EventEntity
import pl.brulion.data.db.FeedKind
import pl.brulion.data.db.HomeworkEntity
import pl.brulion.data.db.ProfileEntity
import java.time.LocalDate
import java.time.LocalTime

class SchoolRemindersTest {
    private val parent = ProfileEntity(id = 1, accountId = 1, synergiaLogin = "123", studentName = "Ola Nowak", isParent = true)
    private val student = ProfileEntity(id = 2, accountId = 2, synergiaLogin = "123u", studentName = "Ola Nowak", isParent = false)
    private val today = LocalDate.of(2026, 11, 11)
    private val afternoon = LocalTime.of(16, 0)
    private val morning = LocalTime.of(9, 30)

    private fun event(
        date: LocalDate,
        type: String = "Sprawdzian",
        subject: String? = "Matematyka",
        topic: String = "",
        id: Long = 1,
    ) = EventEntity(
        profileId = 1, id = id, date = date, lessonNo = 2, startTime = null, typeName = type, color = 0,
        subjectName = subject, teacherName = "Anna Kowalska", topic = topic, addedAt = 0, firstSeenAt = 0,
    )

    private fun homework(due: LocalDate, subject: String? = "Historia", topic: String = "Zadanie", content: String = "") =
        HomeworkEntity(
            profileId = 1, id = 7, dueDate = due, addedDate = null, subjectName = subject, teacherName = null,
            topic = topic, content = content, firstSeenAt = 0,
        )

    private fun due(
        events: List<EventEntity> = emptyList(),
        homework: List<HomeworkEntity> = emptyList(),
        time: LocalTime = afternoon,
        profile: ProfileEntity = parent,
    ) = dueReminders(profile, events, homework, today, time, now = 500)

    @Test
    fun testsAndQuizzesAreRemindedTwoDaysAndOneDayBefore() {
        assertEquals(setOf(2L, 1L), reminderLeadDays("Sprawdzian"))
        assertEquals(setOf(2L, 1L), reminderLeadDays("Kartkówka"))
        assertEquals(setOf(2L, 1L), reminderLeadDays("Praca Klasowa"))
        assertEquals(setOf(2L, 1L), reminderLeadDays("Poprawa"))
        assertEquals(setOf(2L, 1L), reminderLeadDays("Projekt"))
        assertEquals(setOf(2L, 1L), reminderLeadDays("Lektura"))
    }

    @Test
    fun thingsToBringAreRemindedOnlyTheDayBefore() {
        assertEquals(setOf(1L), reminderLeadDays("inne"))
        assertEquals(emptySet<Long>(), reminderLeadDays("Wycieczka"))
    }

    @Test
    fun nothingBeforeReminderHour() {
        assertTrue(due(events = listOf(event(today.plusDays(2))), time = morning).isEmpty())
        assertTrue(due(events = listOf(event(today.plusDays(1))), time = LocalTime.of(15, 59)).isEmpty())
    }

    @Test
    fun twoDaysAheadAndTomorrowGetTheirOwnWording() {
        val two = due(events = listOf(event(today.plusDays(2)))).single()
        assertEquals("Już za dwa dni: Sprawdzian – Matematyka", two.title)
        assertEquals(FeedKind.REMINDER, two.kind)
        assertEquals(today.plusDays(2), two.date)

        val tomorrow = due(events = listOf(event(today.plusDays(1)))).single()
        assertEquals("Już jutro: Sprawdzian – Matematyka", tomorrow.title)
        assertNotEquals(two.refKey, tomorrow.refKey)
    }

    @Test
    fun otherDaysAndSameDayAreNotReminded() {
        assertTrue(due(events = listOf(event(today), event(today.plusDays(3)))).isEmpty())
    }

    @Test
    fun bringItemsOnlyTomorrow() {
        assertTrue(due(events = listOf(event(today.plusDays(2), type = "inne", subject = "Plastyka"))).isEmpty())
        val item = due(events = listOf(event(today.plusDays(1), type = "inne", subject = "Plastyka", topic = "zeszyt, kredki"))).single()
        assertEquals("Już jutro: do przyniesienia – Plastyka", item.title)
        assertEquals("zeszyt, kredki", item.detail)
    }

    @Test
    fun otherCategoriesAreNotReminded() {
        assertTrue(due(events = listOf(event(today.plusDays(1), type = "Wycieczka"))).isEmpty())
    }

    @Test
    fun parentAndStudentProfilesGetTheSameKey() {
        val fromParent = due(events = listOf(event(today.plusDays(1))), profile = parent).single()
        val fromStudent = due(events = listOf(event(today.plusDays(1))), profile = student).single()
        assertEquals(fromParent.childKey, fromStudent.childKey)
        assertEquals(fromParent.refKey, fromStudent.refKey)
    }

    @Test
    fun differentEventsGetDifferentKeys() {
        val a = due(events = listOf(event(today.plusDays(1), subject = "Matematyka"))).single()
        val b = due(events = listOf(event(today.plusDays(1), subject = "Fizyka"))).single()
        assertNotEquals(a.refKey, b.refKey)
    }

    @Test
    fun homeworkIsRemindedTheDayBeforeItsDueDate() {
        assertTrue(due(homework = listOf(homework(today.plusDays(2)))).isEmpty())
        val reminder = due(homework = listOf(homework(today.plusDays(1), content = "Ćwiczenie 3"))).single()
        assertEquals(FeedKind.HOMEWORK_REMINDER, reminder.kind)
        assertEquals("Już jutro: zadanie domowe – Historia", reminder.title)
        assertEquals("Zadanie\nĆwiczenie 3", reminder.detail)
    }

    @Test
    fun sameThingKeyIgnoresCaseAndSpaces() {
        assertEquals(sameThingKey("A ", "Projekt"), sameThingKey("a", " projekt"))
        assertNotEquals(sameThingKey("a"), sameThingKey("b"))
    }
}
