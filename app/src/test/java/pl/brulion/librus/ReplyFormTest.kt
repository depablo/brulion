package pl.brulion.librus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplyFormTest {
    private val html = """
        <form action="/przegladaj_oceny/uczen" method="POST"><input type="hidden" name="zmiany_logowanie" value="x" /></form>
        <FORM ACTION="/wiadomosci" NAME="formWiadomosci" METHOD="POST">
        <input type="hidden" name="requestkey" value="abc" />
        <input type="text" name="temat" id="temat" value="Re: Wycieczka &quot;Zoo&quot; &amp; obiad" class="stretch" >
        <TEXTAREA NAME="tresc"></TEXTAREA>
        <input type="HIDDEN" name="DoKogo" value="3567865">
        <input type="HIDDEN" name="typ" value="odp">
        <INPUT TYPE="submit" VALUE="Wyślij" NAME="wyslij" />
        <INPUT TYPE="submit" VALUE="Anuluj" NAME="anuluj_wysylanie">
        </form>
    """.trimIndent()

    @Test
    fun takesOnlyTheReplyFormWithoutButtons() {
        val fields = replyFormFields(html)
        assertEquals(
            mapOf("requestkey" to "abc", "temat" to "Re: Wycieczka \"Zoo\" & obiad", "DoKogo" to "3567865", "typ" to "odp"),
            fields,
        )
    }

    @Test
    fun noFormWithoutRecipient() {
        assertTrue(replyFormFields("<form><input name=\"temat\" value=\"x\"></form>").isEmpty())
    }

    @Test
    fun decodesNumericEntities() {
        assertEquals("Łódź 'x'", decodeHtmlEntities("&#321;&#xF3;d&#378; &apos;x&apos;"))
        assertEquals("Rada klasowa rodziców &unknown;", decodeHtmlEntities("Rada klasowa rodzic&oacute;w &unknown;"))
    }
}
