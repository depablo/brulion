package pl.brulion.ui.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreshTest {
    private val now = 1_000_000L

    @Test
    fun freshWithinADay() {
        assertTrue(isFreshAt(now - 3600, now))
        assertFalse(isFreshAt(now - 25 * 3600, now))
        assertFalse(isFreshAt(0, now))
    }

    @Test
    fun markedReadClearsWhatCameBefore() {
        assertFalse(isFreshAt(now - 3600, now, markedReadAt = now - 60))
        assertFalse(isFreshAt(now - 60, now, markedReadAt = now - 60))
        assertTrue(isFreshAt(now - 30, now, markedReadAt = now - 60))
    }
}
