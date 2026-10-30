package pl.brulion.data.summary

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import pl.brulion.BrulionApp
import pl.brulion.data.Settings
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class DailySummaryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as BrulionApp).container
        val scheduled = !inputData.getBoolean(KEY_MANUAL, false)
        val outcome = try {
            if (!scheduled || container.settings.summaryEnabled.value) {
                container.dailySummary.run(scheduled, mayWaitForNetwork = scheduled && runAttemptCount < MAX_NETWORK_RETRIES)
            } else {
                DailySummaryService.Outcome.SHOWN
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            container.diagnostics.log("daily summary failed", e)
            DailySummaryService.Outcome.SHOWN
        }
        // No network at summary time: try again a bit later rather than report a quiet day.
        if (outcome == DailySummaryService.Outcome.OFFLINE) return Result.retry()
        // A chain of one-time requests keeps the time exact; periodic work would drift. The next
        // one is planned even after a failure, or the summaries would stop until the app is opened.
        if (scheduled) schedule(applicationContext, container.settings, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "daily_summary"
        private const val WORK_NOW = "daily_summary_now"
        private const val KEY_MANUAL = "manual"
        private const val MAX_NETWORK_RETRIES = 3

        /**
         * Time until the next [minutes] past midnight, more than a minute away. Zoned, so the
         * nights the clocks change don't move the summary by an hour.
         */
        internal fun delayUntilNext(now: ZonedDateTime, minutes: Int): Duration {
            val time = LocalTime.of(minutes / 60, minutes % 60)
            var next = now.toLocalDate().atTime(time).atZone(now.zone)
            if (!next.isAfter(now.plusMinutes(1))) next = now.toLocalDate().plusDays(1).atTime(time).atZone(now.zone)
            return Duration.between(now, next)
        }

        /** Plans the next summary at the configured time; [policy] REPLACE after the time changes. */
        fun schedule(context: Context, settings: Settings, policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) {
            val workManager = WorkManager.getInstance(context)
            if (!settings.summaryEnabled.value) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = OneTimeWorkRequestBuilder<DailySummaryWorker>()
                // Phone time on purpose: the summary comes at 19:00 wherever the parent is.
                .setInitialDelay(delayUntilNext(ZonedDateTime.now(), settings.summaryTime.value).toMillis(), TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                .build()
            workManager.enqueueUniqueWork(WORK_NAME, policy, request)
        }

        /** Sends a summary right away without marking anything as reported. */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<DailySummaryWorker>()
                .setInputData(workDataOf(KEY_MANUAL to true))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NOW, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
