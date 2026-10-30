package pl.brulion.data.summary

import kotlinx.coroutines.flow.first
import pl.brulion.data.AccountRepository
import pl.brulion.data.Settings
import pl.brulion.data.childKey
import pl.brulion.data.sync.SyncController
import pl.brulion.librus.nowSeconds
import pl.brulion.librus.schoolToday

class DailySummaryService(
    private val accounts: AccountRepository,
    private val sync: SyncController,
    private val repository: SummaryRepository,
    private val notifier: SummaryNotifier,
    private val settings: Settings,
) {
    fun canNotify(): Boolean = notifier.canNotify()

    enum class Outcome { SHOWN, OFFLINE }

    /**
     * Syncs every profile and notifies a summary per child. A [scheduled] run remembers
     * what it reported; a manual one (testing from settings) doesn't, so the evening
     * summary still contains everything. With [mayWaitForNetwork], a run that found no
     * network shows nothing and returns [Outcome.OFFLINE], so it can be tried again.
     */
    suspend fun run(scheduled: Boolean, mayWaitForNetwork: Boolean = false): Outcome {
        val profiles = accounts.allProfiles.first()
        val states = profiles.associate { it.id to sync.syncNow(it.id) }
        if (mayWaitForNetwork && isOffline(states.values.toList())) return Outcome.OFFLINE
        // A failed sync still gets a summary from earlier data, but one that says so.

        val now = nowSeconds()
        val today = schoolToday()
        // Parent profiles first, so school data comes from the parent account when there are both.
        val children = profiles.sortedByDescending { it.isParent }.groupBy { childKey(it) }
        for ((key, childProfiles) in children) {
            // Never reach back further than two days, e.g. after the summary was off for a while.
            val since = maxOf(settings.lastSummaryAt(key), now - MAX_LOOKBACK_SECONDS)
            val summary = buildSummary(repository.input(childProfiles, since, today), today)
                .copy(warning = syncProblem(childProfiles.map { states[it.id] }))
            // Moving the window on is safe even after a failure: whatever wasn't fetched gets
            // its firstSeenAt from the sync that eventually fetches it.
            // A quiet day isn't announced unless asked for: a daily "nothing new" gets switched off.
            // Problems with the sync always are, and a summary sent from settings is a test.
            if (!scheduled || !summary.isEmpty || summary.warning != null || settings.summaryWhenQuiet.value) {
                notifier.show(childProfiles.first().id, summary)
            }
            if (scheduled) settings.setLastSummaryAt(key, now)
        }
        return Outcome.SHOWN
    }

    private companion object {
        const val MAX_LOOKBACK_SECONDS = 48 * 60 * 60L
    }
}
