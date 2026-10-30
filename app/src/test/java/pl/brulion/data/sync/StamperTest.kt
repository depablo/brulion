package pl.brulion.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.brulion.data.db.SeenRow

class StamperTest {
    @Test
    fun knownRecordsKeepTheirStamp() {
        val stamper = Stamper(listOf(SeenRow("a", 100), SeenRow("b", 0)), newStamp = 500)
        assertEquals(100, stamper.stamp("a"))
        assertEquals(0, stamper.stamp("b"))
        assertEquals(0, stamper.newCount)
    }

    @Test
    fun newRecordsAreCounted() {
        val stamper = Stamper(emptyList(), newStamp = 500)
        assertEquals(500, stamper.stamp("a"))
        assertEquals(500, stamper.stamp("b"))
        assertEquals(2, stamper.newCount)
    }

    @Test
    fun firstSyncAndBackfillAreNotNew() {
        // First sync of a section: the whole year is already there, nothing is new.
        val first = Stamper(emptyList(), newStamp = 0)
        assertEquals(0, first.stamp("a"))
        assertEquals(0, first.newCount)
        // Missed earlier (sent before the last successful sync), so not news today.
        val later = Stamper(emptyList(), newStamp = 500)
        assertEquals(0, later.stamp("old", backfill = true))
        assertEquals(0, later.newCount)
    }
}
