package pl.brulion.data.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class SentMessageParserTest {
    private fun b64(text: String) = Base64.getEncoder().encodeToString(text.toByteArray())

    private fun item(json: String) = Json.parseToJsonElement(json).jsonObject

    @Test
    fun listItemGivesNameSubjectAndText() {
        val sent = parseSentMessage(
            1,
            item(
                """{"messageId":"77","topic":" Zwolnienie ","content":"${b64("Treść")}",
                   "sendDate":"2026-11-02T21:34:45","receiverFirstName":"Anna","receiverLastName":"Kowalska"}""",
            ),
            read = null,
        )!!

        assertEquals(77L, sent.id)
        assertEquals("Zwolnienie", sent.subject)
        assertEquals("Anna Kowalska", sent.recipients)
        assertEquals("Treść", sent.preview)
        assertNull(sent.readAt)
    }

    @Test
    fun readInfoFromSingleMessageView() {
        val info = parseSentReadInfo(
            item("""{"readDate":"2026-11-02T22:10:00","readedCount":1,"receiversCount":2}"""),
        )

        assertTrue(info.readAt!! > 0)
        assertEquals(1, info.readCount)
        assertEquals(2, info.receiversCount)
    }

    @Test
    fun unreadWhenDetailHasNoReadDate() {
        val info = parseSentReadInfo(item("""{"readDate":null,"readedCount":0,"receiversCount":1}"""))

        assertNull(info.readAt)
        assertEquals(0, info.readCount)
    }

    @Test
    fun receiversArrayTakesPrecedenceAndJoinsNames() {
        val sent = parseSentMessage(
            1,
            item("""{"messageId":"79","receivers":[{"firstName":"Anna","lastName":"Kowalska"},{"name":"Rada klasy"}]}"""),
            read = null,
        )!!

        assertEquals("Anna Kowalska, Rada klasy", sent.recipients)
    }

    @Test
    fun itemWithoutIdIsSkipped() {
        assertNull(parseSentMessage(1, item("""{"topic":"bez id"}"""), read = null))
    }
}
