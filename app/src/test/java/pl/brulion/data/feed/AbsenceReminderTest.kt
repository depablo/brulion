package pl.brulion.data.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.brulion.data.db.AttendanceCategory
import pl.brulion.data.db.AttendanceEntity
import pl.brulion.data.db.FeedKind
import pl.brulion.data.db.ProfileEntity
import java.time.LocalDate

class AbsenceReminderTest {
    private val profile = ProfileEntity(id = 1, accountId = 1, synergiaLogin = "123u", studentName = "Ola Nowak", isParent = true)
    private val today = LocalDate.of(2026, 11, 2) // Monday, ISO week 45

    private fun absence(
        date: LocalDate,
        lessonNo: Int,
        subject: String? = "Matematyka",
        category: AttendanceCategory = AttendanceCategory.ABSENT,
    ) = AttendanceEntity(
        profileId = 1, id = "$date-$lessonNo", date = date, lessonNo = lessonNo, semester = 1, category = category,
        typeName = "", typeShort = "", typeColor = null, subjectName = subject, teacherName = null, addedAt = 0, firstSeenAt = 0,
    )

    @Test
    fun groupsUnexcusedAbsencesOlderThanAWeek() {
        val dayOff = today.minusDays(10)
        val attendance = (1..5).map { absence(dayOff, it, subject = "Przedmiot $it") } +
            absence(today.minusDays(8), 3, subject = "Fizyka") +
            absence(today.minusDays(9), 1, category = AttendanceCategory.EXCUSED) +
            absence(today.minusDays(3), 1) +
            absence(today.minusDays(40), 1)
        val reminder = absenceReminder(profile, attendance, today, now = 99)!!
        assertEquals(FeedKind.ABSENCE_REMINDER, reminder.kind)
        assertEquals("2026-W45", reminder.refKey)
        assertEquals("Do usprawiedliwienia: 6 lekcji", reminder.title)
        assertTrue(reminder.detail!!.contains("e-usprawiedliwienia na librus.pl"))
        assertTrue(reminder.detail!!.endsWith("23.10 (5 lekcji), 25.10 (Fizyka)"))
    }

    @Test
    fun sameWeekSameKeyNextWeekNewOne() {
        val attendance = listOf(absence(today.minusDays(10), 1))
        val monday = absenceReminder(profile, attendance, today, 0)!!.refKey
        assertEquals(monday, absenceReminder(profile, attendance, today.plusDays(4), 0)!!.refKey)
        assertNotEquals(monday, absenceReminder(profile, attendance, today.plusDays(7), 0)!!.refKey)
    }

    @Test
    fun nothingToExcuse() {
        assertNull(absenceReminder(profile, listOf(absence(today.minusDays(2), 1)), today, 0))
        assertNull(absenceReminder(profile, emptyList(), today, 0))
    }

    @Test
    fun polishPlural() {
        assertEquals(
            listOf("1 lekcja", "2 lekcje", "4 lekcje", "5 lekcji", "12 lekcji", "14 lekcji", "22 lekcje", "25 lekcji"),
            listOf(1, 2, 4, 5, 12, 14, 22, 25).map(::lessonsCount),
        )
    }
}
