package pl.brulion.data.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pl.brulion.data.Diagnostics
import pl.brulion.data.db.ProfileEntity
import pl.brulion.librus.nowSeconds

/** App-wide sync state, so every screen sees the same progress and results. */
class SyncController(
    private val repository: SyncRepository,
    private val scope: CoroutineScope,
    private val diagnostics: Diagnostics,
    private val onSynced: () -> Unit = {},
) {

    sealed interface State {
        data object Running : State
        data class Done(val report: SyncReport) : State
        data class Failed(val error: Throwable) : State
    }

    private val _states = MutableStateFlow<Map<Long, State>>(emptyMap())
    val states: StateFlow<Map<Long, State>> = _states

    /** At most one sync per profile; later callers wait for the running one. */
    private val running = mutableMapOf<Long, Deferred<State>>()
    private val runningLock = Mutex()

    fun sync(profileId: Long) {
        scope.launch { syncNow(profileId) }
    }

    /**
     * Syncs and waits for the result; used by background work. Joins a sync of the same
     * profile that is already running instead of starting a second one, and runs in the app
     * scope so a cancelled caller doesn't cut it short.
     */
    suspend fun syncNow(profileId: Long): State {
        val job = runningLock.withLock {
            running[profileId]?.takeIf { it.isActive }
                ?: scope.async { runSync(profileId) }.also { running[profileId] = it }
        }
        return job.await()
    }

    private suspend fun runSync(profileId: Long): State {
        _states.update { it + (profileId to State.Running) }
        val result = try {
            State.Done(repository.sync(profileId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            diagnostics.log("sync $profileId failed", e)
            State.Failed(e)
        }
        if (result is State.Done) {
            result.report.results.forEach { (section, outcome) ->
                when (outcome) {
                    is SectionResult.Failed -> diagnostics.log("sync $profileId $section failed", outcome.error)
                    is SectionResult.Skipped -> diagnostics.log("sync $profileId $section skipped: ${outcome.reason}")
                    is SectionResult.Ok -> outcome.note?.let { diagnostics.log("sync $profileId $section: $it") }
                }
            }
        }
        _states.update { it + (profileId to result) }
        onSynced()
        return result
    }

    /** When each profile's sync last started, so a failing one isn't retried on every resume. */
    private val lastAttemptAt = mutableMapOf<Long, Long>()

    /**
     * Syncs when the data is older than [maxAgeSeconds] and the profile isn't syncing now.
     * After an attempt that didn't move [ProfileEntity.lastSyncAt], waits [RETRY_SECONDS].
     */
    fun syncIfStale(profile: ProfileEntity, maxAgeSeconds: Long = 60 * 60) {
        val now = nowSeconds()
        if (now - profile.lastSyncAt <= maxAgeSeconds) return
        if (_states.value[profile.id] == State.Running) return
        synchronized(lastAttemptAt) {
            if (now - (lastAttemptAt[profile.id] ?: 0) < RETRY_SECONDS) return
            lastAttemptAt[profile.id] = now
        }
        sync(profile.id)
    }

    private companion object {
        const val RETRY_SECONDS = 10 * 60L
    }
}
