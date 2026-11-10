package pl.brulion.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CalendarParsersTest {
    private val users = json(
        """{"Users":[
            {"Id":7,"FirstName":"Anna","LastName":"Kowalska"},
            {"Id":8,"FirstName":"Jan","LastName":"Nowak"}
        ]}"""
    )

    @Test
    fun teacherAbsenceKeepsDatesTimesAndTeacher() {
        val responses = mapOf(
            "Users" to users,
            "Calendars/TeacherFreeDays" to json(
                """{"TeacherFreeDays":[
                    {"Id":1,"Teacher":{"Id":7},"DateFrom":"2026-11-10","DateTo":"2026-11-10",
                     "TimeFrom":"08:00:00","TimeTo":"09:35:00"},
                    {"Id":2,"Teacher":{"Id":8},"DateFrom":"2026-11-12","DateTo":"2026-11-14",
                     "TimeFrom":"00:00:00","TimeTo":"00:00:00"}
                ]}"""
            ),
        )
        val items = withSession(responses) { TeacherAbsenceParser(it, profileId = 5).parse() }

        assertEquals(2, items.size)
        val partial = items.first { it.id == 1L }
        assertEquals(LocalDate.of(2026, 11, 10), partial.dateFrom)
        assertEquals(LocalDate.of(2026, 11, 10), partial.dateTo)
        assertEquals("08:00", partial.timeFrom)
        assertEquals("09:35", partial.timeTo)
        assertEquals("Anna Kowalska", partial.teacherName)
        assertEquals(5L, partial.profileId)

        // Midnight in both times means a whole day, not 00:00.
        val wholeDays = items.first { it.id == 2L }
        assertNull(wholeDays.timeFrom)
        assertNull(wholeDays.timeTo)
        assertEquals(LocalDate.of(2026, 11, 14), wholeDays.dateTo)
        assertEquals("Jan Nowak", wholeDays.teacherName)
    }

    @Test
    fun teacherAbsenceWithoutIdOrDatesIsSkipped() {
        val responses = mapOf(
            "Users" to users,
            "Calendars/TeacherFreeDays" to json(
                """{"TeacherFreeDays":[
                    {"Teacher":{"Id":7},"DateFrom":"2026-11-10","DateTo":"2026-11-10"},
                    {"Id":3,"Teacher":{"Id":7},"DateFrom":"nie-data","DateTo":"2026-11-10"},
                    {"Id":4,"Teacher":{"Id":7},"DateFrom":"2026-11-11","DateTo":"2026-11-11"}
                ]}"""
            ),
        )
        val items = withSession(responses) { TeacherAbsenceParser(it, profileId = 5).parse() }

        assertEquals(listOf(4L), items.map { it.id })
    }

    @Test
    fun ptMeetingKeepsTimeTopicAndTeacher() {
        val responses = mapOf(
            "Users" to users,
            "ParentTeacherConferences" to json(
                """{"ParentTeacherConferences":[
                    {"Id":10,"Topic":"  Wywiadówka   klasowa ","Teacher":{"Id":7},
                     "Date":"2026-12-03","Time":"17:30:00"},
                    {"Id":11,"Topic":"","Teacher":{"Id":8},"Date":"2026-12-04","Time":"00:00:00"}
                ]}"""
            ),
        )
        val items = withSession(responses) { PtMeetingParser(it, profileId = 5).parse() }

        assertEquals(2, items.size)
        val timed = items.first { it.id == 10L }
        assertEquals(LocalDate.of(2026, 12, 3), timed.date)
        assertEquals("17:30", timed.startTime)
        assertEquals("Wywiadówka klasowa", timed.topic)
        assertEquals("Anna Kowalska", timed.teacherName)

        val untimed = items.first { it.id == 11L }
        assertNull(untimed.startTime)
        assertEquals("", untimed.topic)
    }
}
