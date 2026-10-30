package pl.brulion.librus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageHtmlTest {
    private val notice = "Rodzicu,</br>\nw dniu 28/10/2026 dodał uwagę.</br>\nPrzejdź do: <a target=\"_blank\" " +
        "href=\"https://synergia.librus.pl/uwagi\">Uwagi</a>\n, aby zapoznać się.</br>\n</br>\n" +
        "<b>Wiadomość została wygenerowana automatycznie.</b>"

    @Test
    fun detectsHtml() {
        assertTrue(MessageHtml.isHtml(notice))
        assertFalse(MessageHtml.isHtml("Proszę o kontakt <3\nPozdrawiam"))
        assertFalse(MessageHtml.isHtml("a < b > c"))
    }

    @Test
    fun breakTagsReplaceNewlines() {
        assertEquals("a<br> b<br> c", MessageHtml.toHtml("a</br>\nb<br/>\nc"))
        assertEquals("a<br>b", MessageHtml.toHtml("a\nb"))
    }

    @Test
    fun stripsTagsForPlainText() {
        assertEquals(
            "Rodzicu,\nw dniu 28/10/2026 dodał uwagę.\nPrzejdź do: Uwagi , aby zapoznać się.\n\n" +
                "Wiadomość została wygenerowana automatycznie.",
            MessageHtml.toPlainText(notice),
        )
        assertEquals("Zwykły\ntekst", MessageHtml.toPlainText("Zwykły\ntekst"))
        assertEquals("A & B", MessageHtml.toPlainText("<b>A &amp; B</b>"))
    }

    @Test
    fun unwrapsXmlEnvelope() {
        val body = "<Message><Content><![CDATA[Szanowna Pani,\nprzejrzę zeszyty.\n-----\nUżytkownik: X napisał:\nDzień dobry]]></Content><Actions></Actions></Message>"
        assertEquals("Szanowna Pani,\nprzejrzę zeszyty.\n-----\nUżytkownik: X napisał:\nDzień dobry", MessageHtml.unwrap(body))
        assertEquals("Tekst bez końca", MessageHtml.unwrap("<Message><Content><![CDATA[Tekst bez końca"))
        assertEquals("Zwykły tekst", MessageHtml.unwrap("Zwykły tekst"))
        assertEquals("Krótko", MessageHtml.toPlainText("<Message><Content><![CDATA[Krótko]]></Content></Message>"))
    }
}
