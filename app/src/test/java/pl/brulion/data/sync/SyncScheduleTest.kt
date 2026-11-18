package pl.brulion.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

class SyncScheduleTest {
    private val warsaw = ZoneId.of("Europe/Warsaw")
    private fun at(text: String) = ZonedDateTime.of(LocalDateTime.parse(text), warsaw)

    // 16 November 2026 is a Monday.

    @Test
    fun beforeTheFirstSchoolDayRun() {
        assertEquals(at("2026-11-16T06:00"), SyncWorker.nextRun(at("2026-11-16T05:30")))
    }

    @Test
    fun exactlyOnASlotGoesToTheNext() {
        assertEquals(at("2026-11-16T07:00"), SyncWorker.nextRun(at("2026-11-16T06:00")))
    }

    @Test
    fun hourlyUntilThree() {
        assertEquals(at("2026-11-16T15:00"), SyncWorker.nextRun(at("2026-11-16T14:20")))
    }

    @Test
    fun afterThreeEveryThreeHours() {
        assertEquals(at("2026-11-16T18:00"), SyncWorker.nextRun(at("2026-11-16T15:30")))
        assertEquals(at("2026-11-16T21:00"), SyncWorker.nextRun(at("2026-11-16T18:00")))
        assertEquals(at("2026-11-16T23:00"), SyncWorker.nextRun(at("2026-11-16T21:00")))
    }

    @Test
    fun noRunAtNightOnASchoolDay() {
        // After the last run at 23:00 the next one is 6:00 the next morning.
        assertEquals(at("2026-11-17T06:00"), SyncWorker.nextRun(at("2026-11-16T23:00")))
        assertEquals(at("2026-11-17T06:00"), SyncWorker.nextRun(at("2026-11-16T23:45")))
        assertEquals(at("2026-11-17T06:00"), SyncWorker.nextRun(at("2026-11-17T03:10")))
    }

    @Test
    fun fridayNightGoesToSaturdayMorning() {
        assertEquals(at("2026-11-21T06:00"), SyncWorker.nextRun(at("2026-11-20T23:30")))
    }

    @Test
    fun weekendEveryThreeHours() {
        // 21 November 2026 is a Saturday.
        assertEquals(at("2026-11-21T09:00"), SyncWorker.nextRun(at("2026-11-21T06:00")))
        assertEquals(at("2026-11-21T12:00"), SyncWorker.nextRun(at("2026-11-21T10:00")))
        assertEquals(at("2026-11-21T21:00"), SyncWorker.nextRun(at("2026-11-21T18:30")))
    }

    @Test
    fun weekendNightGoesToMondayMorning() {
        // Sunday 22 November, after the last weekend run at 21:00, skips to Monday 6:00.
        assertEquals(at("2026-11-23T06:00"), SyncWorker.nextRun(at("2026-11-22T21:00")))
    }
}
