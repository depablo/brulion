package pl.brulion.data.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import pl.brulion.BrulionApp
import java.util.concurrent.TimeUnit

/** Daily check; notifies once per new version. */
class UpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as BrulionApp).container
        val updates = container.updates
        val info = try {
            updates.check()
        } catch (e: Exception) {
            return Result.success() // Tomorrow's check will try again.
        }
        if (info != null && updates.notifiedVersion != info.versionCode && UpdateNotifier(applicationContext).show(info)) {
            updates.notifiedVersion = info.versionCode
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "update_check"

        fun schedule(context: Context, enabled: Boolean) {
            val workManager = WorkManager.getInstance(context)
            if (!enabled) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<UpdateWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
