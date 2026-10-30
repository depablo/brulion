package pl.brulion.data.db

import org.junit.Assert.assertEquals
import org.junit.Test

class MessageAudienceTest {
    @Test
    fun countDecides() {
        assertEquals(MessageAudience.Direct, MessageAudience.of(1, inOtherInboxes = false))
        assertEquals(MessageAudience.Group(212), MessageAudience.of(212, inOtherInboxes = true))
    }

    @Test
    fun otherInboxesHintWithoutCount() {
        assertEquals(MessageAudience.Several, MessageAudience.of(null, inOtherInboxes = true))
        assertEquals(MessageAudience.Unknown, MessageAudience.of(null, inOtherInboxes = false))
        assertEquals(MessageAudience.Unknown, MessageAudience.of(0, inOtherInboxes = false))
    }
}
