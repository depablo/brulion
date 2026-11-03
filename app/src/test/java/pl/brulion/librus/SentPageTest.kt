package pl.brulion.librus

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SentPageTest {
    private val menuPage = "<html><title>LIBRUS Synergia</title><div>Wyloguj Pomoc Ustawienia</div></html>"
    private val formPage = "<form><textarea name=\"tresc\"></textarea></form>"

    @Test
    fun backOnMessagesPageWithoutFormIsSent() {
        assertTrue(sentPageConfirmed("https://synergia.librus.pl/wiadomosci", menuPage))
    }

    @Test
    fun formStillShownIsNotSent() {
        assertFalse(sentPageConfirmed("https://synergia.librus.pl/wiadomosci", formPage))
    }

    @Test
    fun otherPageIsNotSent() {
        assertFalse(sentPageConfirmed("https://synergia.librus.pl/logowanie", menuPage))
    }

    @Test
    fun explicitConfirmationTextIsSent() {
        assertTrue(sentPageConfirmed("https://synergia.librus.pl/wiadomosci", "<p>Wiadomość została wysłana</p>"))
    }
}
