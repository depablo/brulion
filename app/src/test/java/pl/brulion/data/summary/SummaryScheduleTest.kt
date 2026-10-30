package pl.brulion.data.summary

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

class SummaryScheduleTest {
    private val warsaw = ZoneId.of("Europe/Warsaw")
    private fun at(text: String) = ZonedDateTime.of(java.time.LocalDateTime.parse(text), warsaw)
    private val seven = 19 * 60

    @Test
    fun laterToday() {
        assertEquals(Duration.ofHours(3), DailySummaryWorker.delayUntilNext(at("2026-10-30T16:00"), seven))
    }

    @Test
    fun justMissedGoesToTomorrow() {
        assertEquals(Duration.ofHours(24), DailySummaryWorker.delayUntilNext(at("2026-10-30T19:00"), seven))
    }

    @Test
    fun clocksGoingBackAddAnHour() {
        // 25 October 2026: 3:00 CEST becomes 2:00 CET.
        assertEquals(Duration.ofHours(24), DailySummaryWorker.delayUntilNext(at("2026-10-24T20:00"), seven))
    }

    @Test
    fun clocksGoingForwardTakeAnHour() {
        // 29 March 2026: 2:00 CET becomes 3:00 CEST.
        assertEquals(Duration.ofHours(22), DailySummaryWorker.delayUntilNext(at("2026-03-28T20:00"), seven))
    }
}
