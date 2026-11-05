package pl.brulion.librus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessagesPageGuardsTest {

    @Test
    fun outOfRangeNumericEntityIsKeptAsWritten() {
        assertEquals("a &#99999999; b", decodeHtmlEntities("a &#99999999; b"))
        assertEquals("a &#x110000; b", decodeHtmlEntities("a &#x110000; b"))
    }

    @Test
    fun validEntitiesAreDecoded() {
        assertEquals("ł ó", decodeHtmlEntities("&#322; &oacute;"))
    }

    @Test
    fun loginRedirectIsDetected() {
        assertTrue(endedOnLoginPage("https://synergia.librus.pl/loguj?next=/wiadomosci"))
        assertTrue(endedOnLoginPage("https://portal.librus.pl/konto-librus/login"))
    }

    @Test
    fun messagesPageIsNotALoginRedirect() {
        assertFalse(endedOnLoginPage("https://synergia.librus.pl/wiadomosci/2/5?x=1"))
    }
}
