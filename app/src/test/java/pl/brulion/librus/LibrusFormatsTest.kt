package pl.brulion.librus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class LibrusFormatsTest {
    @Test
    fun dateTimeIsWarsawLocalTime() {
        // 12:00 in Warsaw in winter (CET) is 11:00 UTC.
        assertEquals(1767524400L, parseLibrusDateTime("2026-01-04 12:00:00"))
        // In summer (CEST) the offset is +2.
        assertEquals(1783159200L, parseLibrusDateTime("2026-07-04 12:00:00"))
        assertNull(parseLibrusDateTime("garbage"))
        assertNull(parseLibrusDateTime(null))
    }

    @Test
    fun dateAndTime() {
        assertEquals(LocalDate.of(2026, 10, 28), parseLibrusDate("2026-10-28"))
        assertEquals("08:00", parseLibrusTime("08:00:00"))
        assertNull(parseLibrusTime("8"))
    }
}
