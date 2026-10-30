package pl.brulion.data.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import pl.brulion.BrulionApp

/** Receives PackageInstaller results for the session started by [UpdateManager]. */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val updates = (context.applicationContext as BrulionApp).container.updates
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            // The system's "update this app?" screen; the user started the update, so the app is in front.
            updates.confirm(IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java))
            return
        }
        updates.onInstallResult(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
    }
}
