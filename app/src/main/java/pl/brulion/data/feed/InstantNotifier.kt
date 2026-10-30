package pl.brulion.data.feed

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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pl.brulion.R
import pl.brulion.data.AccountRepository
import pl.brulion.data.Announcements
import pl.brulion.data.Settings
import pl.brulion.data.childKey
import pl.brulion.data.db.BrulionDatabase
import pl.brulion.data.db.FeedEntryEntity
import pl.brulion.data.db.FeedKind
import pl.brulion.librus.nowSeconds
import pl.brulion.ui.MainActivity

/**
 * Notifies new feed entries of the kinds the user picked, right after the sync that brought
 * them, one notification per child. The evening summary still comes on its own.
 */
class InstantNotifier(
    private val context: Context,
    private val db: BrulionDatabase,
    private val accounts: AccountRepository,
    private val announcements: Announcements,
    private val settings: Settings,
) {
    private val lock = Mutex()

    /** Starts from what's already there, so turning it on doesn't replay the past two weeks. */
    suspend fun enable(kinds: Set<FeedKind>) = lock.withLock {
        if (settings.instantKinds.value.isEmpty()) settings.instantSeenFeedId = db.feedDao().maxId() ?: 0
        settings.setInstantKinds(kinds)
    }

    suspend fun check() = lock.withLock {
        val seen = settings.instantSeenFeedId
        val kinds = settings.instantKinds.value
        if (seen < 0 || kinds.isEmpty()) {
            settings.instantSeenFeedId = db.feedDao().maxId() ?: 0
            return@withLock
        }
        val fresh = db.feedDao().after(seen)
        if (fresh.isEmpty()) return@withLock
        settings.instantSeenFeedId = fresh.maxOf { it.id }
        val hidden = announcements.observeHiddenIds().first()
        val shown = pickNew(fresh, kinds, hidden, nowSeconds())
        if (shown.isEmpty() || !canNotify()) return@withLock

        val profiles = accounts.allProfiles.first()
        for ((child, entries) in shown.groupBy { it.childKey }) {
            val profile = profiles.firstOrNull { childKey(it) == child } ?: continue
            show(child, profile.id, profile.studentName.substringBefore(' '), entries)
        }
    }

    private fun show(child: String, profileId: Long, name: String, entries: List<FeedEntryEntity>) {
        createChannel()
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_PROFILE_ID, profileId)
            .putExtra(MainActivity.EXTRA_OPEN_FEED, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            context, child.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val lines = entries.map { listOfNotNull(it.title, it.detail?.takeIf { d -> d.isNotBlank() }).joinToString(" · ") }
        val title = if (entries.size == 1) name else context.getString(R.string.instant_title, name, entries.size)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(lines.first())
            .setStyle(NotificationCompat.InboxStyle().also { style -> lines.take(MAX_LINES).forEach(style::addLine) })
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_TAG, child.hashCode(), notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call; nothing to do.
        }
    }

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.instant_channel), NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = context.getString(R.string.instant_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "instant"
        private const val NOTIFICATION_TAG = "instant"
        private const val MAX_LINES = 6
        /** Older entries are catching up (e.g. a new account's first sync), not news. */
        private const val MAX_AGE_SECONDS = 3 * 24 * 60 * 60L

        /**
         * The entries worth a notification: picked kinds, recent, not a hidden announcement,
         * and once per child even when both its profiles logged the same thing (messages).
         */
        internal fun pickNew(entries: List<FeedEntryEntity>, kinds: Set<FeedKind>, hiddenAnnouncements: Set<String>, now: Long) =
            entries.filter { it.kind in kinds && now - it.occurredAt <= MAX_AGE_SECONDS }
                .filterNot { it.kind == FeedKind.ANNOUNCEMENT && it.refKey in hiddenAnnouncements }
                .distinctBy { listOf(it.childKey, it.kind, it.title, it.detail) }
    }
}
