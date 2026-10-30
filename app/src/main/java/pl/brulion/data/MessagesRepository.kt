package pl.brulion.data

import pl.brulion.data.db.BrulionDatabase
import pl.brulion.data.db.MessageAttachmentEntity
import pl.brulion.data.sync.parseMessageDetails
import pl.brulion.librus.LibrusMessages
import pl.brulion.librus.Recipient
import pl.brulion.librus.RecipientGroup
import pl.brulion.librus.nowSeconds
import java.io.File

/** Reading, downloading attachments and replying; listing is part of the regular sync. */
class MessagesRepository(
    private val db: BrulionDatabase,
    private val sessions: MessageSessions,
    private val filesDir: File,
    private val diagnostics: Diagnostics,
) {
    /** Fetches the full message and stores it; Librus marks it as read. */
    suspend fun open(profileId: Long, messageId: Long) {
        val json = withSession(profileId) { it.message(messageId) }
        val now = nowSeconds()
        val details = parseMessageDetails(profileId, messageId, json, now)
        db.messageDao().saveDetails(details.body, details.attachments, readAt = now)
    }

    /** Returns the downloaded file, reusing an earlier download. */
    suspend fun attachment(profileId: Long, attachment: MessageAttachmentEntity): File {
        val target = File(filesDir, "attachments/$profileId/${attachment.id}/${safeFileName(attachment.fileName)}")
        if (target.isFile && target.length() > 0) return target
        withSession(profileId) { it.downloadAttachment(attachment.messageId, attachment.id, target) }
        return target
    }

    suspend fun reply(profileId: Long, messageId: Long, text: String) {
        withSession(profileId) { it.reply(messageId, text) }
    }

    suspend fun recipientGroups(profileId: Long): List<RecipientGroup> = withSession(profileId) { it.recipientGroups() }

    suspend fun recipients(profileId: Long, group: RecipientGroup): List<Recipient> =
        withSession(profileId) { it.recipients(group) }

    suspend fun send(profileId: Long, group: RecipientGroup, recipients: List<Recipient>, topic: String, text: String) {
        withSession(profileId) { it.send(group, recipients, topic, text) }
    }

    private suspend fun <T> withSession(profileId: Long, block: suspend (LibrusMessages.Session) -> T): T = try {
        sessions.withSession(profileId, block)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        diagnostics.log("messages $profileId failed", e)
        throw e
    }

    private fun safeFileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().ifEmpty { "zalacznik" }
}
