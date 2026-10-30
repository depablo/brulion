package pl.brulion.data.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class MessageParserTest {
    private fun b64(text: String) = Base64.getEncoder().encodeToString(text.toByteArray())

    @Test
    fun decodesPolishContent() {
        assertEquals("Zażółć gęślą jaźń\nlinia 2", decodeContent(b64("Zażółć gęślą jaźń\r\nlinia 2\n")))
        assertEquals("", decodeContent(null))
        assertEquals("", decodeContent("not base64 !!"))
    }

    @Test
    fun parsesInboxItem() {
        val item = Json.parseToJsonElement(
            """{"messageId":"123","senderFirstName":"Jan","senderLastName":"Nowak","topic":" Wycieczka ",
               "content":"${b64("Treść")}","sendDate":"2026-10-26T15:41:17","readDate":null,"isAnyFileAttached":true}"""
        ).jsonObject
        val message = parseMessage(7, item)!!
        assertEquals(123L, message.id)
        assertEquals("Jan Nowak", message.senderName)
        assertEquals("Wycieczka", message.subject)
        assertEquals("Treść", message.preview)
        assertNotNull(message.sentAt)
        assertNull(message.readAt)
        assertEquals(true, message.hasAttachments)
        assertEquals(false, message.automatic)
    }

    @Test
    fun recognizesAutomaticNotices() {
        val item = Json.parseToJsonElement("""{"messageId":"9","topic":"Dodano uwagę","category":"6","sendDate":"2026-10-30T08:16:21"}""").jsonObject
        assertEquals(true, parseMessage(7, item)!!.automatic)
    }
}
