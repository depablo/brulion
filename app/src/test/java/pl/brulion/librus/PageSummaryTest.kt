package pl.brulion.librus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PageSummaryTest {

    @Test
    fun keepsTitleAndVisibleTextWithoutScriptsOrFormFields() {
        val html = """
            <html><head><title> Wiadomości
              Librus </title><script>var x = "secret";</script></head>
            <body><div class="alert">Błąd   wysyłania</div>
            <form><textarea name="tresc">moja treść wiadomości</textarea></form></body></html>
        """.trimIndent()

        val summary = summarizePage(html)

        assertEquals("title \"Wiadomości Librus\", text \"Błąd wysyłania\"", summary)
        assertFalse(summary.contains("secret"))
        assertFalse(summary.contains("moja treść"))
    }

    @Test
    fun cutsLongTextAndHandlesMissingTitle() {
        val summary = summarizePage("<p>${"a".repeat(500)}</p>")

        assertEquals("title \"\", text \"${"a".repeat(200)}\"", summary)
    }
}
