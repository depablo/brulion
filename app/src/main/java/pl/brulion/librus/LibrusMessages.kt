/*
 * Part of Brulion, GNU GPL v3. Based on Librus support in Szkolny.eu
 * (https://github.com/szkolny-eu/szkolny-android), (c) Kuba Szczodrzyński and contributors, GPL v3.
 */
package pl.brulion.librus

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.FormBody
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import pl.brulion.librus.LibrusException.Reason
import java.io.File
import java.io.IOException

/**
 * The messages module (wiadomosci.librus.pl) isn't available through API 2.0 for portal
 * accounts, so we open a Synergia web session with an auto-login token and use the JSON API
 * behind the messages web app. Replies go through the Synergia reply form.
 */
class LibrusMessages(private val http: LibrusHttp) {

    /** [autoLoginToken] comes from `POST AutoLoginToken` in API 2.0 and is single-use. */
    suspend fun open(autoLoginToken: String): Session {
        val web = http.webClient()
        // Sets the Synergia session cookies. A rejected token ends on the login page; reported as
        // an expired token, so the caller retries once with a new one.
        val login = http.execute(browser("${Librus.SYNERGIA_URL}/loguj/token/$autoLoginToken/przenies/uczen/widok/centrum_powiadomien"), web)
        if ("centrum_powiadomien" !in login.finalUrl) throw LibrusException(Reason.TOKEN_EXPIRED, "auto-login ended on ${login.finalUrl.substringBefore('?')}")
        // Hands the session over to the messages domain. With a mobile UA it ends on an error page,
        // but the session cookie is set on the way, which is all we need (a desktop UA ends on the
        // portal without a session).
        http.execute(browser("${Librus.SYNERGIA_URL}/wiadomosci2"), web)
        return Session(web)
    }

    /** A logged-in web session shared by Synergia and the messages module. */
    inner class Session internal constructor(private val web: OkHttpClient) {

        /** Newest received messages; listing doesn't mark anything as read. */
        suspend fun inbox(limit: Int = 50): List<JsonObject> {
            val json = getJson("api/inbox/messages?page=1&limit=$limit")
            return json["data"]?.jsonArray?.map { it.jsonObject }.orEmpty()
        }

        /** Full message with its body and attachment list. Librus marks it as read. */
        suspend fun message(id: Long): JsonObject =
            getJson("api/inbox/messages/$id").obj("data") ?: throw LibrusException(Reason.NOT_FOUND, "message $id")

        /** Downloads an attachment into [target], going through the Librus file sandbox. */
        suspend fun downloadAttachment(messageId: Long, attachmentId: Long, target: File) {
            val link = getJson("api/attachments/$attachmentId/messages/$messageId").obj("data")?.string("downloadLink")
                ?: throw LibrusException(Reason.NOT_FOUND, "attachment $attachmentId")
            if ("CSDownloadFailed" in link) throw LibrusException(Reason.NOT_FOUND, "attachment $attachmentId")

            val key = SINGLE_USE_KEY.find(link)?.groupValues?.get(1)
            val request = if (key == null) {
                browser("$link/get")
            } else {
                // The sandbox prepares the file asynchronously; wait until it says it's ready.
                waitForSandbox(key)
                browser("$SANDBOX_URL?action=CSDownload&singleUseKey=$key").newBuilder()
                    .post(FormBody.Builder().build())
                    .build()
            }
            save(request, target)
        }

        /** Replies to message [id] with [text], keeping the subject Librus proposes ("Re: …"). */
        suspend fun reply(id: Long, text: String) {
            val page = http.execute(browser("${Librus.SYNERGIA_URL}/wiadomosci/3/5/$id"), web)
            val fields = replyFormFields(page.body)
            if (page.code != 200 || "DoKogo" !in fields) {
                // A message form without the known fields means Librus changed it; no form at all,
                // that the message can't be answered.
                val reason = if ("formWiadomosci" in page.body) Reason.FORM_CHANGED else Reason.ACCESS_DENIED
                throw LibrusException(reason, "no reply form for message $id")
            }
            // The web form comes with the original quoted below; sent along, as the browser would.
            val quote = replyQuote(page.body)
            submit(MultipartBody.Builder().setType(MultipartBody.FORM).apply {
                fields.forEach { (name, value) -> addFormDataPart(name, value) }
                addFormDataPart("tresc", if (quote.isBlank()) text else "$text\n\n$quote")
                addFormDataPart("wyslij", "Wyślij")
            }.build())
        }

        /** Kinds of recipients the school allows, e.g. "Wychowawca", "Nauczyciele", "Sekretariat". */
        suspend fun recipientGroups(): List<RecipientGroup> = recipientGroups(composePage())
            .ifEmpty { throw LibrusException(Reason.FORM_CHANGED, "no recipient groups on the compose page") }

        /** People in [group]; each id goes into `DoKogo[]` when sending. */
        suspend fun recipients(group: RecipientGroup): List<Recipient> {
            val page = composePage()
            val token = CSRF_TOKEN.find(page)?.groupValues?.get(1)
                ?: throw LibrusException(Reason.FORM_CHANGED, "no CSRF token on the compose page")
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("typAdresata", group.type)
                .addFormDataPart("poprzednia", "5")
                .addFormDataPart("tabZaznaczonych", "")
                .addFormDataPart("czyWirtualneKlasy", "false")
                .addFormDataPart("idGrupy", "0")
                .apply {
                    group.classId?.let {
                        addFormDataPart("klasa_rada_rodzicow", it)
                        addFormDataPart("klasa_opiekunowie", it)
                        addFormDataPart("klasa_rodzice", it)
                    }
                }
                .build()
            val result = http.execute(
                browser("${Librus.SYNERGIA_URL}/getRecipients").newBuilder()
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Referer", "${Librus.SYNERGIA_URL}/wiadomosci/2/5")
                    .header("requestkey", token)
                    .post(body)
                    .build(),
                web,
            )
            if (result.code != 200) throw LibrusException(Reason.UNKNOWN, "recipients: HTTP ${result.code}")
            return parseRecipients(result.body)
        }

        /** Sends a new message to [recipients], all from the same [group]. */
        suspend fun send(group: RecipientGroup, recipients: List<Recipient>, topic: String, text: String) {
            // A fresh page, so the form's request key is current.
            val page = freshComposePage()
            val fields = formFields(page, "name=\"temat\"")
            if ("requestkey" !in fields) throw LibrusException(Reason.FORM_CHANGED, "no compose form")
            val body = MultipartBody.Builder().setType(MultipartBody.FORM).apply {
                fields.forEach { (name, value) -> if (name != "temat") addFormDataPart(name, value) }
                addFormDataPart("adresat", group.type)
                recipients.forEach { addFormDataPart("DoKogo[]", it.id) }
                addFormDataPart("temat", topic)
                addFormDataPart("tresc", text)
                addFormDataPart("wyslij", "Wyślij")
            }.build()
            submit(body)
        }

        private var composePage: String? = null
        private var composePageAt = 0L

        /** The compose form carries CSRF and request keys, so it's reused only briefly. */
        private suspend fun composePage(): String =
            composePage?.takeIf { nowSeconds() - composePageAt < COMPOSE_PAGE_TTL } ?: freshComposePage()

        private suspend fun freshComposePage(): String =
            http.execute(browser("${Librus.SYNERGIA_URL}/wiadomosci/2/5"), web).body.also {
                composePage = it
                composePageAt = nowSeconds()
            }

        /**
         * Without the confirmation the message may still have gone out (e.g. the response timed
         * out), so the caller mustn't suggest a blind retry.
         */
        private suspend fun submit(body: MultipartBody) {
            val result = try {
                http.execute(
                    browser("${Librus.SYNERGIA_URL}/wiadomosci").newBuilder().post(body).build(),
                    web,
                )
            } catch (e: LibrusException) {
                throw LibrusException(Reason.SEND_UNCONFIRMED, e.detail, e)
            }
            if (SENT_CONFIRMATION !in result.body) {
                throw LibrusException(Reason.SEND_UNCONFIRMED, "message not confirmed (HTTP ${result.code})")
            }
        }

        private suspend fun getJson(path: String): JsonObject {
            val result = http.execute(browser("${Librus.MESSAGES_URL}/$path"), web)
            val json = result.json()
            return when {
                result.code == 200 && json != null -> json
                result.code == 401 -> throw LibrusException(Reason.TOKEN_EXPIRED, "messages session")
                result.code == 404 -> throw LibrusException(Reason.NOT_FOUND, path.substringBefore('?'))
                else -> throw LibrusException(Reason.UNKNOWN, "messages: HTTP ${result.code}")
            }
        }

        private suspend fun waitForSandbox(key: String) {
            repeat(SANDBOX_TRIES) {
                val result = http.execute(
                    browser("$SANDBOX_URL?action=CSCheckKey").newBuilder()
                        .post(FormBody.Builder().add("singleUseKey", key).build())
                        .build(),
                    web,
                )
                when (result.json()?.string("status")) {
                    "ready" -> return
                    "not_downloaded_yet" -> delay(2_000)
                    else -> throw LibrusException(Reason.UNKNOWN, "sandbox: HTTP ${result.code}")
                }
            }
            throw LibrusException(Reason.UNKNOWN, "sandbox: file not ready")
        }

        private suspend fun save(request: Request, target: File) = withContext(Dispatchers.IO) {
            val partial = File(target.parentFile, target.name + ".part")
            try {
                web.newCall(request).execute().use { response ->
                    val body = response.body
                    if (!response.isSuccessful || body == null) {
                        throw LibrusException(Reason.UNKNOWN, "download: HTTP ${response.code}")
                    }
                    target.parentFile?.mkdirs()
                    partial.outputStream().use { body.byteStream().copyTo(it) }
                }
                if (!partial.renameTo(target)) throw LibrusException(Reason.UNKNOWN, "download: cannot save")
            } catch (e: IOException) {
                partial.delete()
                throw LibrusException(Reason.NETWORK, e.message, e)
            }
        }
    }

    private fun browser(url: String) = Request.Builder().url(url).header("User-Agent", Librus.BROWSER_USER_AGENT).build()

    private companion object {
        const val SANDBOX_URL = "https://sandbox.librus.pl/index.php"
        const val SANDBOX_TRIES = 10
        const val SENT_CONFIRMATION = "została wysłana"
        const val COMPOSE_PAGE_TTL = 2 * 60L
        val SINGLE_USE_KEY = Regex("singleUseKey=([\\w-]+)")
        val CSRF_TOKEN = Regex("var csrfTokenValue = \"([^\"]+)\"")
    }
}

/** A kind of recipient on Synergia's compose page; [classId] is set for class-bound groups like parents. */
data class RecipientGroup(val type: String, val label: String, val classId: String?)

data class Recipient(val id: String, val name: String)

/** Name/value pairs of the inputs in Synergia's reply form, without its submit buttons. */
internal fun replyFormFields(html: String): Map<String, String> = formFields(html, "name=\"DoKogo\"")

/** The quoted original Synergia puts into the reply's text area. */
internal fun replyQuote(html: String): String =
    Regex("<textarea\\b[^>]*name=\"tresc\"[^>]*>([\\s\\S]*?)</textarea>", RegexOption.IGNORE_CASE).find(html)
        ?.groupValues?.get(1)?.let(::decodeHtmlEntities)?.replace("\r\n", "\n")?.trim().orEmpty()

/**
 * Name/value pairs of the inputs in the form that contains [anchor], skipping buttons,
 * radios and checkboxes (those are filled in by the caller).
 */
internal fun formFields(html: String, anchor: String): Map<String, String> {
    val at = html.indexOf(anchor, ignoreCase = true)
    if (at < 0) return emptyMap()
    val start = html.lastIndexOf("<form", at, ignoreCase = true)
    val end = html.indexOf("</form>", at, ignoreCase = true)
    if (start < 0 || end < 0) return emptyMap()
    return Regex("<input\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(html.substring(start, end))
        .map { it.value }
        .filterNot { Regex("type\\s*=\\s*\"(submit|radio|checkbox)\"", RegexOption.IGNORE_CASE).containsMatchIn(it) }
        .mapNotNull { tag ->
            val name = attribute(tag, "name") ?: return@mapNotNull null
            name to decodeHtmlEntities(attribute(tag, "value").orEmpty())
        }
        .toMap()
}

private fun attribute(tag: String, name: String): String? =
    Regex("\\b$name\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)

internal fun decodeHtmlEntities(text: String): String =
    Regex("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);").replace(text) { match ->
        val entity = match.groupValues[1]
        if (entity.startsWith("#")) {
            val code = if (entity.startsWith("#x")) entity.drop(2).toIntOrNull(16) else entity.drop(1).toIntOrNull()
            code?.let { String(Character.toChars(it)) } ?: match.value
        } else {
            NAMED_ENTITIES[entity] ?: match.value
        }
    }

// Synergia writes "ó" as a named entity; other Polish letters come as numeric ones.
private val NAMED_ENTITIES = mapOf(
    "amp" to "&", "quot" to "\"", "lt" to "<", "gt" to ">", "apos" to "'", "nbsp" to " ",
    "oacute" to "ó", "Oacute" to "Ó", "eacute" to "é", "aacute" to "á", "ndash" to "–", "mdash" to "—",
    "bdquo" to "„", "rdquo" to "”", "ldquo" to "“", "hellip" to "…",
)

/**
 * Recipient kinds from the radio buttons on the compose page. Custom groups ("grupa")
 * need another selection step and are left out.
 */
internal fun recipientGroups(html: String): List<RecipientGroup> =
    Regex("<input\\b[^>]*name=\"adresat\"[^>]*>", RegexOption.IGNORE_CASE).findAll(html).mapNotNull { match ->
        val tag = match.value
        val call = Regex("selectRecipients\\(\"([\\w-]+)\"[^)]*\\)").find(tag) ?: return@mapNotNull null
        val type = call.groupValues[1]
        if (type == "grupa") return@mapNotNull null
        val args = call.value.substringAfter('(').substringBefore(')').split(',').map { it.trim() }
        val id = attribute(tag, "id")
        val label = id?.let { Regex("<label[^>]*for=\"$it\"[^>]*>([\\s\\S]*?)</label>", RegexOption.IGNORE_CASE).find(html) }
            ?.groupValues?.get(1)?.let(::stripTags)
        RecipientGroup(type = type, label = label?.ifBlank { null } ?: type, classId = args.getOrNull(3)?.takeIf { it.all(Char::isDigit) })
    }.distinctBy { it.type }.toList()

/** Rows of the recipients table returned by `getRecipients`. */
internal fun parseRecipients(html: String): List<Recipient> =
    Regex("<tr[^>]*>([\\s\\S]*?)</tr>", RegexOption.IGNORE_CASE).findAll(html).mapNotNull { row ->
        val input = Regex("<input\\b[^>]*name=\"DoKogo\\[\\]\"[^>]*>", RegexOption.IGNORE_CASE).find(row.value)?.value
            ?: return@mapNotNull null
        val id = attribute(input, "value")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val name = Regex("<label[^>]*>([\\s\\S]*?)</label>", RegexOption.IGNORE_CASE).find(row.value)?.groupValues?.get(1)
            ?.let(::stripTags)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        Recipient(id, name)
    }.distinctBy { it.id }.toList()

private fun stripTags(html: String): String =
    decodeHtmlEntities(html.replace(Regex("<[^>]+>"), " ")).replace(Regex("\\s+"), " ").trim()
