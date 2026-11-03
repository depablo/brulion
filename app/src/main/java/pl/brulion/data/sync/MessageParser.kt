/*
 * Part of Brulion, GNU GPL v3. Based on Librus support in Szkolny.eu
 * (https://github.com/szkolny-eu/szkolny-android), (c) Kuba Szczodrzyński and contributors, GPL v3.
 */
package pl.brulion.data.sync

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import pl.brulion.data.db.MessageAttachmentEntity
import pl.brulion.data.db.SentMessageEntity
import pl.brulion.data.db.MessageBodyEntity
import pl.brulion.data.db.MessageEntity
import pl.brulion.librus.MessageHtml
import pl.brulion.librus.bool
import pl.brulion.librus.int
import pl.brulion.librus.long
import pl.brulion.librus.normalizeSpaces
import pl.brulion.librus.objects
import pl.brulion.librus.parseLibrusDateTime
import pl.brulion.librus.string
import java.util.Base64

/** Category of notices Synergia sends by itself, e.g. about a new note. */
private const val AUTOMATIC_CATEGORY = "6"

/** One item of `wiadomosci.librus.pl/api/inbox/messages`; dates are Warsaw local time. */
internal fun parseMessage(profileId: Long, item: JsonObject): MessageEntity? {
    val id = item.string("messageId")?.toLongOrNull() ?: return null
    val sender = listOfNotNull(item.string("senderFirstName"), item.string("senderLastName"))
        .joinToString(" ")
        .ifBlank { item.string("senderName").orEmpty() }
    return MessageEntity(
        profileId = profileId,
        id = id,
        senderName = sender.normalizeSpaces(),
        subject = item.string("topic").orEmpty().trim(),
        preview = MessageHtml.toPlainText(decodeContent(item.string("content"))),
        sentAt = parseLibrusDateTime(item.string("sendDate")) ?: 0,
        readAt = parseLibrusDateTime(item.string("readDate")),
        hasAttachments = item.bool("isAnyFileAttached") == true,
        firstSeenAt = 0,
        automatic = item.string("category") == AUTOMATIC_CATEGORY,
    )
}

/**
 * One item of `wiadomosci.librus.pl/api/outbox/messages`. The field names are assumed to match the
 * inbox list; the sync logs the real ones (see SyncRepository) so a mismatch shows up in diagnostics.
 */
internal fun parseSentMessage(profileId: Long, item: JsonObject, read: SentReadInfo?): SentMessageEntity? {
    val id = item.string("messageId")?.toLongOrNull() ?: return null
    return SentMessageEntity(
        profileId = profileId,
        id = id,
        recipients = recipientNames(item),
        subject = item.string("topic").orEmpty().trim(),
        preview = MessageHtml.toPlainText(decodeContent(item.string("content"))),
        sentAt = parseLibrusDateTime(item.string("sendDate")) ?: 0,
        readAt = read?.readAt,
        readCount = read?.readCount,
        receiversCount = read?.receiversCount,
    )
}

/** Read state of a sent message, from its single-message view. The list has no read date. */
internal class SentReadInfo(val readAt: Long?, val readCount: Int?, val receiversCount: Int?)

internal fun parseSentReadInfo(detail: JsonObject) = SentReadInfo(
    readAt = parseLibrusDateTime(detail.string("readDate")),
    readCount = detail.int("readedCount"),
    receiversCount = detail.int("receiversCount"),
)

/** Names of the people a sent message went to, comma-separated. */
private fun recipientNames(item: JsonObject): String {
    val fromList = (item["receivers"] as? JsonArray)?.objects().orEmpty().mapNotNull { receiver ->
        listOfNotNull(receiver.string("firstName"), receiver.string("lastName"))
            .joinToString(" ")
            .ifBlank { receiver.string("name").orEmpty() }
            .normalizeSpaces()
            .ifEmpty { null }
    }
    val names = fromList.ifEmpty {
        listOfNotNull(item.string("receiverFirstName"), item.string("receiverLastName"))
            .joinToString(" ")
            .normalizeSpaces()
            .ifEmpty { item.string("receiverName").orEmpty().normalizeSpaces() }
            .let { listOfNotNull(it.ifEmpty { null }) }
    }
    return names.joinToString(", ")
}

class MessageDetails(val body: MessageBodyEntity, val attachments: List<MessageAttachmentEntity>)

/** `wiadomosci.librus.pl/api/inbox/messages/{id}`; fetching it marks the message as read in Librus. */
internal fun parseMessageDetails(profileId: Long, messageId: Long, json: JsonObject, now: Long): MessageDetails {
    val attachments = (json["attachments"] as? JsonArray)?.objects().orEmpty().mapNotNull {
        MessageAttachmentEntity(
            profileId = profileId,
            messageId = messageId,
            id = it.string("id")?.toLongOrNull() ?: it.long("id") ?: return@mapNotNull null,
            fileName = it.string("filename").orEmpty().ifBlank { "zalacznik" },
        )
    }
    val body = MessageBodyEntity(
        profileId = profileId,
        messageId = messageId,
        body = decodeContent(json.string("Message")),
        canReply = json.int("noReply") != 1 && json.string("noReply") != "1",
        fetchedAt = now,
        receiversCount = json.int("receiversCount")?.takeIf { it > 0 },
    )
    return MessageDetails(body, attachments)
}

/** The inbox returns the body as base64-encoded UTF-8 text, sometimes in an XML envelope. */
internal fun decodeContent(content: String?): String {
    if (content.isNullOrEmpty()) return ""
    return runCatching { String(Base64.getDecoder().decode(content), Charsets.UTF_8) }
        .getOrDefault("")
        .replace("\r\n", "\n")
        .let(MessageHtml::unwrap)
        .trim()
}
