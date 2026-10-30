package pl.brulion.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {
    @Test
    fun ignoresCaseAndPolishLetters() {
        assertTrue(matchesQuery("zolc", "Zażółć gęślą jaźń"))
        assertTrue(matchesQuery("LODZ", null, "Wycieczka do Łodzi"))
        assertTrue(matchesQuery("", "cokolwiek"))
    }

    @Test
    fun everyWordSomewhere() {
        assertTrue(matchesQuery("obiady listopad", "Nowak Anna", "obiady listopad"))
        assertTrue(matchesQuery("nowak obiady", "Nowak Anna", "obiady listopad"))
        assertFalse(matchesQuery("obiady grudzień", "Nowak Anna", "obiady listopad"))
    }
}
