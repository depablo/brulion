package pl.brulion.data.db

/**
 * Who a received message was written to. Librus gives only the number of recipients, and only
 * in the message details, which can't be fetched for unread messages without marking them read.
 */
sealed interface MessageAudience {
    /** Written to this account only. */
    data object Direct : MessageAudience
    data class Group(val count: Int) : MessageAudience
    /** The count isn't known yet, but the message reached several of the user's inboxes. */
    data object Several : MessageAudience
    data object Unknown : MessageAudience

    companion object {
        fun of(receiversCount: Int?, inOtherInboxes: Boolean): MessageAudience = when {
            receiversCount == 1 -> Direct
            receiversCount != null && receiversCount > 1 -> Group(receiversCount)
            inOtherInboxes -> Several
            else -> Unknown
        }
    }
}
