/*
 * Part of Brulion, GNU GPL v3. Based on Librus support in Szkolny.eu
 * (https://github.com/szkolny-eu/szkolny-android), (c) Kuba Szczodrzyński and contributors, GPL v3.
 */
package pl.brulion.librus

import kotlinx.serialization.json.JsonObject
import okhttp3.FormBody
import okhttp3.Request
import pl.brulion.librus.LibrusException.Reason

/** Librus portal (Konto Librus) OAuth and the list of linked Synergia accounts. */
class PortalAuth(private val http: LibrusHttp) {

    class Tokens(val accessToken: String, val refreshToken: String, val expiresAt: Long)

    class SynergiaAccount(
        val id: Int,
        val login: String,
        val accessToken: String,
        val studentName: String,
        val isParent: Boolean,
    )

    suspend fun exchangeCode(code: String): Tokens = token(
        FormBody.Builder()
            .add("client_id", Librus.CLIENT_ID)
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("redirect_uri", Librus.REDIRECT_URL)
            .build()
    )

    suspend fun refresh(refreshToken: String): Tokens = token(
        FormBody.Builder()
            .add("client_id", Librus.CLIENT_ID)
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .build()
    )

    private suspend fun token(body: FormBody): Tokens {
        val result = http.execute(Request.Builder().url(Librus.PORTAL_TOKEN_URL).post(body).build())
        val json = result.json() ?: throw LibrusException(Reason.UNKNOWN, "HTTP ${result.code}")
        if (result.code != 200) {
            val error = json.string("hint") ?: json.string("error")
            val reason = when (error) {
                "Authorization code has expired",
                "Authorization code has been revoked",
                "Cannot decrypt the refresh token",
                "Token has been revoked" -> Reason.REAUTH_REQUIRED
                else -> Reason.UNKNOWN
            }
            throw LibrusException(reason, error)
        }
        return Tokens(
            accessToken = json.string("access_token") ?: throw LibrusException(Reason.UNKNOWN, "no access_token"),
            refreshToken = json.string("refresh_token") ?: throw LibrusException(Reason.UNKNOWN, "no refresh_token"),
            expiresAt = nowSeconds() + (json.long("expires_in") ?: 86400),
        )
    }

    /** [needingAction] names accounts that can't be used until fixed on the portal. */
    class SynergiaAccounts(val usable: List<SynergiaAccount>, val needingAction: List<String>)

    suspend fun synergiaAccounts(portalToken: String): SynergiaAccounts {
        val json = get(portalToken, "/v3/SynergiaAccounts")
        val usable = mutableListOf<SynergiaAccount>()
        val needingAction = mutableListOf<String>()
        for (account in json.array("accounts")?.objects().orEmpty()) {
            val studentName = account.string("studentName").orEmpty().trim()
            val login = account.string("login")
            when (account.string("state")) {
                // Often an old account, e.g. of a sibling who left school; it shouldn't block the others.
                "requiring_an_action", "need-activation" -> {
                    needingAction += studentName.ifEmpty { login.orEmpty() }
                    continue
                }
            }
            usable += SynergiaAccount(
                id = account.int("id") ?: continue,
                login = login ?: continue,
                accessToken = account.string("accessToken") ?: continue,
                studentName = studentName,
                isParent = account.string("group") == "parent",
            )
        }
        if (usable.isEmpty() && needingAction.isNotEmpty()) {
            throw LibrusException(Reason.ACCOUNT_DISCONNECTED, "${needingAction.size} account(s) require an action")
        }
        return SynergiaAccounts(usable, needingAction)
    }

    /** Returns a fresh Synergia API token for the given Synergia login. */
    suspend fun freshSynergiaToken(portalToken: String, login: String): String {
        val json = get(portalToken, "/v3/SynergiaAccounts/fresh/$login")
        return json.string("accessToken") ?: throw LibrusException(Reason.UNKNOWN, "no accessToken")
    }

    private suspend fun get(portalToken: String, endpoint: String): JsonObject {
        val result = http.execute(
            Request.Builder()
                .url(Librus.PORTAL_API_URL + endpoint)
                .header("Authorization", "Bearer $portalToken")
                .build()
        )
        val json = result.json() ?: throw LibrusException(Reason.UNKNOWN, "HTTP ${result.code}")
        if (result.code != 200) {
            val error = json.string("reason") ?: json.string("message") ?: json.string("hint") ?: json.string("Code")
            val reason = when (error) {
                "requires_an_action" -> Reason.ACCOUNT_DISCONNECTED
                "Access token is invalid", "Error while decoding to JSON" -> Reason.REAUTH_REQUIRED
                "Account not found" -> Reason.NOT_FOUND
                "Unable to refresh the account", "ApiDisabled" -> Reason.MAINTENANCE
                else -> Reason.UNKNOWN
            }
            throw LibrusException(reason, error)
        }
        return json
    }
}
