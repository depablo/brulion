package pl.brulion.librus

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class LibrusJustificationsTest {

    @Test
    fun lessonsRequestMatchesWhatTheLibrusWebAppSends() {
        val json = LibrusJustifications.Request(
            dateFrom = LocalDate.of(2026, 10, 12),
            dateTo = LocalDate.of(2026, 10, 12),
            lessons = listOf(2, 3),
            message = "Proszę o usprawiedliwienie nieobecności.",
            notifyTeachers = true,
        ).toJson()

        assertEquals(
            """{"messageFromParent":"Proszę o usprawiedliwienie nieobecności.","lessons":[2,3],"dateFrom":"2026-10-12","dateTo":"2026-10-12","sendNotify":true}""",
            json.toString(),
        )
    }

    @Test
    fun daysRequestHasNoLessons() {
        val json = LibrusJustifications.Request(
            dateFrom = LocalDate.of(2026, 10, 12),
            dateTo = LocalDate.of(2026, 10, 14),
            lessons = emptyList(),
            message = "",
            notifyTeachers = false,
        ).toJson()

        assertEquals("[]", json["lessons"].toString())
        assertEquals("2026-10-14", json.string("dateTo"))
        assertEquals(false, json.bool("sendNotify"))
    }
}
