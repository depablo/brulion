package pl.brulion.data.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import pl.brulion.BuildConfig

/** Runs in the new version right after it replaced the old one. */
class UpdatedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Debug builds are replaced on every install from the computer.
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED || BuildConfig.DEBUG) return
        UpdateNotifier(context).showInstalled()
    }
}
