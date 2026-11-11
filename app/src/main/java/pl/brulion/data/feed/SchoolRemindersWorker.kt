package pl.brulion.data.feed

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import pl.brulion.BrulionApp
import pl.brulion.data.Settings
import pl.brulion.data.summary.DailySummaryWorker
import pl.brulion.librus.SCHOOL_ZONE
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Runs the school reminders at [REMINDER_HOUR] every day. Like the evening summary, it is a chain
 * of one-time requests, so the time stays exact, and the next one is planned even after a failure.
 */
class SchoolRemindersWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as BrulionApp).container
        try {
            container.reminders.check()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            container.diagnostics.log("school reminders failed", e)
        }
        schedule(applicationContext, container.settings, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "school_reminders"

        /** Plans the next run at [REMINDER_HOUR]; [policy] REPLACE when the setting changes. */
        fun schedule(context: Context, settings: Settings, policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP) {
            val workManager = WorkManager.getInstance(context)
            if (!settings.remindersEnabled.value) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }
            val delay = DailySummaryWorker.delayUntilNext(ZonedDateTime.now(SCHOOL_ZONE), REMINDER_HOUR * 60)
            val request = OneTimeWorkRequestBuilder<SchoolRemindersWorker>()
                .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.MINUTES)
                .build()
            workManager.enqueueUniqueWork(WORK_NAME, policy, request)
        }
    }
}
