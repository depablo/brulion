package pl.brulion

import android.app.Application
import pl.brulion.data.summary.DailySummaryWorker
import pl.brulion.data.sync.SyncWorker
import pl.brulion.data.update.UpdateWorker

class BrulionApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.diagnostics.installCrashHandler()
        DailySummaryWorker.schedule(this, container.settings)
        SyncWorker.schedule(this)
        UpdateWorker.schedule(this, container.updates.isConfigured && container.settings.updateAutoCheck.value)
    }
}
