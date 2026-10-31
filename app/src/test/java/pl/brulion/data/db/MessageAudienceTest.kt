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
    fun groupSizeBands() {
        assertEquals(MessageAudience.GroupSize.Few, MessageAudience.Group(2).size)
        assertEquals(MessageAudience.GroupSize.Few, MessageAudience.Group(9).size)
        assertEquals(MessageAudience.GroupSize.Class, MessageAudience.Group(10).size)
        assertEquals(MessageAudience.GroupSize.Class, MessageAudience.Group(39).size)
        assertEquals(MessageAudience.GroupSize.Many, MessageAudience.Group(40).size)
        assertEquals(MessageAudience.GroupSize.Many, MessageAudience.Group(212).size)
    }

    @Test
    fun otherInboxesHintWithoutCount() {
        assertEquals(MessageAudience.Several, MessageAudience.of(null, inOtherInboxes = true))
        assertEquals(MessageAudience.Unknown, MessageAudience.of(null, inOtherInboxes = false))
        assertEquals(MessageAudience.Unknown, MessageAudience.of(0, inOtherInboxes = false))
    }
}
