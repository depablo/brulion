package pl.brulion.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import pl.brulion.BrulionApp
import pl.brulion.librus.SCHOOL_ZONE
import java.time.DayOfWeek
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Keeps the data, and with it the widget, fresh between app visits; changes such as a
 * cancelled lesson show up without opening the app. Runs at the times in [nextRun]: on school
 * days hourly from 6:00 to 15:00, then at 18:00, 21:00 and 23:00; at weekends every 3 hours
 * from 6:00 to 21:00. Nothing between 23:00 and 6:00.
 *
 * Like the reminders, it is a chain of one-time requests, so each run plans the next one.
 */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as BrulionApp).container
        // Failures are logged by the controller; the next run tries again.
        for (profile in container.accounts.allProfiles.first()) container.sync.syncNow(profile.id)
        schedule(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "background_sync_schedule"

        /** Name of the old 3-hourly periodic job; it is cancelled so it doesn't run next to the schedule. */
        private const val OLD_WORK_NAME = "background_sync"

        private val SCHOOL_DAY_HOURS = (6..15).toList() + listOf(18, 21, 23)
        private val WEEKEND_HOURS = listOf(6, 9, 12, 15, 18, 21)

        /** Plans the next run; [policy] APPEND_OR_REPLACE from a run that just finished. */
        fun schedule(context: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) {
            val workManager = WorkManager.getInstance(context)
            workManager.cancelUniqueWork(OLD_WORK_NAME)
            val now = ZonedDateTime.now(SCHOOL_ZONE)
            val delay = Duration.between(now, nextRun(now))
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
            workManager.enqueueUniqueWork(WORK_NAME, policy, request)
        }

        /** The first scheduled run strictly after [now], in its time zone. */
        internal fun nextRun(now: ZonedDateTime): ZonedDateTime {
            for (dayOffset in 0L..7L) {
                val date = now.toLocalDate().plusDays(dayOffset)
                for (hour in hoursFor(date.dayOfWeek)) {
                    val slot = date.atTime(hour, 0).atZone(now.zone)
                    if (slot.isAfter(now)) return slot
                }
            }
            error("no sync slot within a week of $now")
        }

        private fun hoursFor(day: DayOfWeek): List<Int> =
            if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) WEEKEND_HOURS else SCHOOL_DAY_HOURS
    }
}
