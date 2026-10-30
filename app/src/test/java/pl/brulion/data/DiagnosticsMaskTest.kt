package pl.brulion.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DiagnosticsMaskTest {
    @Test
    fun masksSecrets() {
        assertEquals(
            "GET https://synergia.librus.pl/loguj/token/***/przenies -> 302",
            Diagnostics.mask("GET https://synergia.librus.pl/loguj/token/abc123/przenies -> 302"),
        )
        assertEquals(
            "POST index.php?action=CSDownload&singleUseKey=*** Authorization: Bearer ***",
            Diagnostics.mask("POST index.php?action=CSDownload&singleUseKey=w4_17_ab Authorization: Bearer eyJ.abc-1"),
        )
        assertEquals("code=*** password=***", Diagnostics.mask("code=xyz password=secret"))
    }
}
