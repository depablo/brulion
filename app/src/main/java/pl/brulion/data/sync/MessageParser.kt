/*
 * Part of Brulion, GNU GPL v3. Based on Librus support in Szkolny.eu
 * (https://github.com/szkolny-eu/szkolny-android), (c) Kuba Szczodrzyński and contributors, GPL v3.
 */
package pl.brulion.data.sync

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import pl.brulion.data.db.MessageAttachmentEntity
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
