package pl.brulion.data.feed

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import pl.brulion.R
import pl.brulion.data.db.FeedEntryEntity
import pl.brulion.data.db.ProfileEntity
import pl.brulion.ui.MainActivity

/**
 * The weekly reminder about absences still to excuse. Excusing happens on librus.pl (the
 * e-usprawiedliwienia module), so the notification can open it; tapping it shows the feed.
 */
class AbsenceNotifier(private val context: Context) {

    fun show(profile: ProfileEntity, reminder: FeedEntryEntity) {
        if (!canNotify()) return
        createChannel()
        val id = reminder.childKey.hashCode()
        val open = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_PROFILE_ID, profile.id)
            .putExtra(MainActivity.EXTRA_OPEN_FEED, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val librus = Intent(Intent.ACTION_VIEW, Uri.parse(LIBRUS_URL))
        val name = profile.studentName.substringBefore(' ')
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$name · ${reminder.title}")
            .setContentText(reminder.detail?.substringBefore('\n'))
            .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.detail))
            .setContentIntent(PendingIntent.getActivity(context, id, open, FLAGS))
            .addAction(0, context.getString(R.string.absence_open_librus), PendingIntent.getActivity(context, id + 1, librus, FLAGS))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_TAG, id, notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call; nothing to do.
        }
    }

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.absence_channel), NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = context.getString(R.string.absence_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "absences"
        const val NOTIFICATION_TAG = "absences"
        const val LIBRUS_URL = "https://portal.librus.pl/rodzina"
        const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }
}
