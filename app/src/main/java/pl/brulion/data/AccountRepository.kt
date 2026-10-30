package pl.brulion.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pl.brulion.data.db.AccountEntity
import pl.brulion.data.db.BrulionDatabase
import pl.brulion.data.db.ProfileEntity
import pl.brulion.librus.Librus
import pl.brulion.librus.LibrusApi
import pl.brulion.librus.LibrusException
import pl.brulion.librus.LibrusException.Reason
import pl.brulion.librus.PortalAuth
import pl.brulion.librus.nowSeconds

class AccountRepository(
    private val db: BrulionDatabase,
    private val portal: PortalAuth,
    private val api: LibrusApi,
) {
    private val accounts = db.accountDao()
    private val profiles = db.profileDao()

    /** Serializes token refreshes so parallel requests don't burn the same refresh token twice. */
    private val tokenMutex = Mutex()

    val allProfiles: Flow<List<ProfileEntity>> = profiles.observeAll()

    class Imported(val profiles: List<ProfileEntity>, val needingAction: List<String>)

    /**
     * Finishes the portal WebView login: exchanges the OAuth code and imports every usable
     * linked student. Accounts that need fixing on the portal are skipped and named in the result.
     */
    suspend fun addPortalAccount(code: String): Imported {
        val tokens = portal.exchangeCode(code)
        val found = portal.synergiaAccounts(tokens.accessToken)
        val synergiaAccounts = found.usable
        if (synergiaAccounts.isEmpty()) throw LibrusException(Reason.NOT_FOUND, "no Synergia accounts")

        return db.withTransaction {
            val accountId = accounts.insert(
                AccountEntity(
                    portalAccessToken = tokens.accessToken,
                    portalRefreshToken = tokens.refreshToken,
                    portalTokenExpiresAt = tokens.expiresAt,
                )
            )
            val imported = synergiaAccounts.map { account ->
                upsertProfile(
                    ProfileEntity(
                        accountId = accountId,
                        synergiaLogin = account.login,
                        studentName = account.studentName,
                        isParent = account.isParent,
                        apiAccessToken = account.accessToken,
                        apiTokenExpiresAt = nowSeconds() + Librus.PORTAL_SYNERGIA_TOKEN_TTL,
                    )
                )
            }
            accounts.deleteOrphans()
            Imported(imported, found.needingAction)
        }
    }

    /** Logging in again with the same student replaces the old credentials instead of duplicating them. */
    private suspend fun upsertProfile(profile: ProfileEntity): ProfileEntity {
        val existing = profiles.findByLogin(profile.synergiaLogin)
        return if (existing != null) {
            // Keep what the app knows about the data; only the credentials are new. A reset
            // lastSyncAt would hide everything that arrived while the login was broken.
            profile.copy(id = existing.id, lastSyncAt = existing.lastSyncAt).also { profiles.update(it) }
        } else {
            profile.copy(id = profiles.insert(profile))
        }
    }

    suspend fun removeProfile(profileId: Long) {
        db.withTransaction {
            profiles.delete(profileId)
            accounts.deleteOrphans()
        }
    }

    /** Runs [block] with a valid API token, refreshing it once if Librus reports it as expired. */
    suspend fun <T> withApi(profileId: Long, block: suspend (token: String) -> T): T {
        val token = apiToken(profileId, forceRefresh = false)
        return try {
            block(token)
        } catch (e: LibrusException) {
            if (e.reason != Reason.TOKEN_EXPIRED) throw e
            block(apiToken(profileId, forceRefresh = true))
        }
    }

    suspend fun me(profileId: Long): LibrusApi.Me = withApi(profileId) { api.me(it) }

    private suspend fun apiToken(profileId: Long, forceRefresh: Boolean): String = tokenMutex.withLock {
        val profile = profiles.get(profileId) ?: throw LibrusException(Reason.NOT_FOUND, "profile $profileId")
        val token = profile.apiAccessToken
        if (!forceRefresh && token != null && profile.apiTokenExpiresAt - 60 > nowSeconds()) {
            return token
        }
        val account = accounts.get(profile.accountId)
            ?: throw LibrusException(Reason.REAUTH_REQUIRED, "account missing")

        val fresh = portal.freshSynergiaToken(portalToken(account), profile.synergiaLogin)
        val refreshed = profile.copy(
            apiAccessToken = fresh,
            apiTokenExpiresAt = nowSeconds() + Librus.PORTAL_SYNERGIA_TOKEN_TTL,
        )
        profiles.update(refreshed)
        fresh
    }

    private suspend fun portalToken(account: AccountEntity): String {
        val token = account.portalAccessToken
        if (token != null && account.portalTokenExpiresAt - 60 > nowSeconds()) return token
        val refreshToken = account.portalRefreshToken ?: throw LibrusException(Reason.REAUTH_REQUIRED)
        val tokens = portal.refresh(refreshToken)
        accounts.update(
            account.copy(
                portalAccessToken = tokens.accessToken,
                portalRefreshToken = tokens.refreshToken,
                portalTokenExpiresAt = tokens.expiresAt,
            )
        )
        return tokens.accessToken
    }
}
