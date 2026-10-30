package pl.brulion.data.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class MessageDetailsTest {
    private fun details(extra: String) = parseMessageDetails(
        profileId = 1,
        messageId = 7,
        json = Json.parseToJsonElement(
            """{"Message":"${Base64.getEncoder().encodeToString("Treść".toByteArray())}",$extra}"""
        ).jsonObject,
        now = 100,
    )

    @Test
    fun readsReceiversAndAttachments() {
        val result = details(""""noReply":0,"receiversCount":95,"attachments":[{"filename":"menu.docx","id":"11365773"}]""")
        assertEquals("Treść", result.body.body)
        assertEquals(95, result.body.receiversCount)
        assertEquals(listOf(11365773L), result.attachments.map { it.id })
    }

    @Test
    fun missingCountAndNoReply() {
        val result = details(""""noReply":"1","attachments":[]""")
        assertNull(result.body.receiversCount)
        assertFalse(result.body.canReply)
    }
}
