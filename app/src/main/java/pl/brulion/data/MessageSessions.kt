package pl.brulion.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pl.brulion.librus.LibrusApi
import pl.brulion.librus.LibrusException
import pl.brulion.librus.LibrusException.Reason
import pl.brulion.librus.LibrusMessages
import pl.brulion.librus.nowSeconds
import pl.brulion.librus.string

/**
 * Web sessions of the messages module, one per profile, shared by the sync and the message
 * screens. Opening one takes an auto-login token and three requests, so it's reused for a while.
 */
class MessageSessions(
    private val accounts: AccountRepository,
    private val api: LibrusApi,
    private val messages: LibrusMessages,
) {
    private class Cached(val session: LibrusMessages.Session, val openedAt: Long)

    private val sessions = mutableMapOf<Long, Cached>()
    private val lock = Mutex()

    /** Runs [block] in a reused session, opening a new one once if it has expired. */
    suspend fun <T> withSession(profileId: Long, block: suspend (LibrusMessages.Session) -> T): T = try {
        block(session(profileId, fresh = false))
    } catch (e: LibrusException) {
        if (e.reason != Reason.TOKEN_EXPIRED) throw e
        block(session(profileId, fresh = true))
    }

    private suspend fun session(profileId: Long, fresh: Boolean): LibrusMessages.Session = lock.withLock {
        val now = nowSeconds()
        sessions[profileId]?.takeIf { !fresh && now - it.openedAt < SESSION_TTL }?.let { return it.session }
        val token = accounts.withApi(profileId) { api.get(it, "AutoLoginToken", post = true) }.string("Token")
            ?: throw LibrusException(Reason.UNKNOWN, "no AutoLoginToken")
        messages.open(token).also { sessions[profileId] = Cached(it, now) }
    }

    private companion object {
        /** Synergia logs out idle sessions after roughly half an hour; stay well below. */
        const val SESSION_TTL = 10 * 60L
    }
}
