package pl.brulion.data.summary

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import pl.brulion.R
import pl.brulion.ui.MainActivity

class SummaryNotifier(private val context: Context) {

    fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** One notification per child; [profileId] is opened when it's tapped. */
    fun show(profileId: Long, summary: DailySummary) {
        if (!canNotify()) return
        createChannel()
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_PROFILE_ID, profileId)
            .putExtra(MainActivity.EXTRA_OPEN_FEED, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context,
            profileId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(summary.title)
            .setContentText(summary.headline)
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary.text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setGroup(GROUP)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_TAG, profileId.toInt(), notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call; nothing to do.
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.summary_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.summary_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "daily_summary"
        const val GROUP = "com.bonsaponsi.brulion.SUMMARY"
        const val NOTIFICATION_TAG = "summary"
    }
}
