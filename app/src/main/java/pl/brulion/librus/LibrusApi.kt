/*
 * Part of Brulion, GNU GPL v3. Based on Librus support in Szkolny.eu
 * (https://github.com/szkolny-eu/szkolny-android), (c) Kuba Szczodrzyński and contributors, GPL v3.
 */
package pl.brulion.librus

import kotlinx.serialization.json.JsonObject
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import pl.brulion.librus.LibrusException.Reason

/** Librus REST API 2.0 (api.librus.pl). */
class LibrusApi(private val http: LibrusHttp) {

    class Me(
        val accountId: Int,
        val login: String,
        val isParent: Boolean,
        val studentName: String,
        val accountName: String,
    )

    private suspend fun raw(token: String, endpoint: String, post: Boolean = false): HttpResult = http.execute(
        Request.Builder()
            .url("${Librus.API_URL}/$endpoint")
            .header("Authorization", "Bearer $token")
            .apply { if (post) post(ByteArray(0).toRequestBody()) }
            .build()
    )

    suspend fun get(token: String, endpoint: String, post: Boolean = false): JsonObject {
        val result = raw(token, endpoint, post)
        if (result.code == 503) throw LibrusException(Reason.MAINTENANCE)
        val json = result.json()
        if (result.code != 200) {
            val error = json?.string("Code") ?: json?.string("Message") ?: json?.string("Status")
            val reason = when (error) {
                "TokenIsExpired" -> Reason.TOKEN_EXPIRED
                "Request is denied", "AccessDeny", "Insufficient scopes" -> Reason.ACCESS_DENIED
                "Resource not found", "NotFound" -> Reason.NOT_FOUND
                "LuckyNumberIsNotActive", "NotesIsNotActive" -> Reason.FEATURE_DISABLED
                "Maintenance" -> Reason.MAINTENANCE
                else -> if (result.code == 401) Reason.TOKEN_EXPIRED else Reason.UNKNOWN
            }
            throw LibrusException(reason, error ?: "HTTP ${result.code}")
        }
        return json ?: throw LibrusException(Reason.UNKNOWN, "empty response")
    }

    suspend fun me(token: String): Me {
        val me = get(token, "Me").obj("Me") ?: throw LibrusException(Reason.UNKNOWN, "no Me")
        val account = me.obj("Account")
        val user = me.obj("User")
        return Me(
            accountId = account?.int("Id") ?: 0,
            login = account?.string("Login").orEmpty(),
            // GroupId 5 and 6 are parent/guardian accounts.
            isParent = account?.int("GroupId") in 5..6,
            studentName = fullName(user?.string("FirstName"), user?.string("LastName")),
            accountName = fullName(account?.string("FirstName"), account?.string("LastName")),
        )
    }

    private fun fullName(first: String?, last: String?) =
        listOfNotNull(first?.trim(), last?.trim()).filter { it.isNotEmpty() }.joinToString(" ")
}
