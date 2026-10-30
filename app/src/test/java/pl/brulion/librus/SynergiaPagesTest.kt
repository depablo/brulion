package pl.brulion.librus

import org.junit.Assert.assertEquals
import org.junit.Test

/** The message forms as Synergia serves them (trimmed and anonymized copies in test resources). */
class SynergiaPagesTest {
    private fun page(name: String) = javaClass.getResource("/librus/$name")!!.readText()

    @Test
    fun replyForm() {
        val html = page("reply_page.html")
        assertEquals(
            mapOf(
                "requestkey" to "TEST-REQUEST-KEY",
                "filtrUzytkownikow" to "0",
                "idPojemnika" to "",
                "temat" to "Re: Zbiórka \"Pluszaki\"",
                "poprzednia" to "5",
                "DoKogo" to "1000002",
                "Wid" to "2000001",
                "idWiadomosciOrg" to "2000001",
                "idOdpowiadajacego" to "0",
                "typ" to "odp",
                "fileStorageIdentifier" to "",
            ),
            replyFormFields(html),
        )
        assertEquals(
            "-----\nUżytkownik: Kowalska Anna (Kowalska Anna) [Nauczyciel] 2026-10-28 20:10:38 napisał:\n" +
                "Szanowni Państwo,\ntreść oryginalnej wiadomości.",
            replyQuote(html),
        )
    }

    @Test
    fun composeForm() {
        val html = page("compose_page.html")
        val fields = formFields(html, "name=\"temat\"")
        assertEquals("TEST-REQUEST-KEY", fields["requestkey"])
        assertEquals("0", fields["Rodzaj"])
        assertEquals("5", fields["poprzednia"])
        assertEquals("TEST-CSRF-TOKEN", Regex("var csrfTokenValue = \"([^\"]+)\"").find(html)?.groupValues?.get(1))
    }

    @Test
    fun recipientGroupsFromRadios() {
        assertEquals(
            listOf(
                RecipientGroup("wychowawca", "Wychowawcy", null),
                RecipientGroup("rada_rodzicow", "Rada klasowa rodziców", "40001"),
                RecipientGroup("szkolna_rada_rodzicow", "Szkolna rada rodziców", null),
                RecipientGroup("nauczyciel", "Nauczyciele", null),
                RecipientGroup("admin", "Administrator Szkoły", null),
                RecipientGroup("sekretariat", "Sekretariat", null),
                RecipientGroup("sadmin", "Firma LIBRUS (SuperAdministrator) Pomoc Techniczna", null),
            ),
            recipientGroups(page("compose_page.html")),
        )
    }

    @Test
    fun recipientsTable() {
        assertEquals(listOf(Recipient("1000001", "Kowalska Anna")), parseRecipients(page("recipients.html")))
    }
}
