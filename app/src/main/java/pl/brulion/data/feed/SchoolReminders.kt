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
import pl.brulion.R
import pl.brulion.data.AccountRepository
import pl.brulion.data.Settings
import pl.brulion.data.childKey
import pl.brulion.data.db.BrulionDatabase
import pl.brulion.data.db.EventEntity
import pl.brulion.data.db.FeedEntryEntity
import pl.brulion.data.db.FeedKind
import pl.brulion.data.db.HomeworkEntity
import pl.brulion.data.db.ProfileEntity
import pl.brulion.librus.SCHOOL_ZONE
import pl.brulion.librus.nowSeconds
import pl.brulion.ui.MainActivity
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/** Hour (school time) when the reminders about upcoming events and homework go out. */
internal const val REMINDER_HOUR = 16

/** Calendar categories reminded about two days and one day before. Matched lowercase. */
private val TWO_DAY_REMINDER_TYPES = setOf("sprawdzian", "kartkówka", "praca klasowa", "poprawa", "projekt", "lektura")

/** Things to bring; reminded only the day before. */
private const val BRING_TYPE = "inne"

/**
 * The days before an event of this calendar category that get a reminder: 2 and 1 for tests and
 * the like, 1 for things to bring, none for other categories (they are not reminded at all).
 */
internal fun reminderLeadDays(typeName: String): Set<Long> {
    val type = typeName.trim().lowercase()
    return when {
        type in TWO_DAY_REMINDER_TYPES -> setOf(2L, 1L)
        type == BRING_TYPE -> setOf(1L)
        else -> emptySet()
    }
}

/**
 * The reminders due now for one profile. A reminder is due from [REMINDER_HOUR] on the day
 * before (or two days before) the event. An item that appeared after that point goes out
 * at once, and one that only appeared on the eve gets only the "tomorrow" line, never a stale
 * "in two days".
 *
 * Parent and student profiles of one child see the same events. The key is built from what
 * the event says, not from the profile, so the feed's unique index keeps only one entry per
 * child and the notification goes out once.
 */
internal fun dueReminders(
    profile: ProfileEntity,
    events: List<EventEntity>,
    homework: List<HomeworkEntity>,
    today: LocalDate,
    time: LocalTime,
    now: Long,
): List<FeedEntryEntity> {
    if (time < LocalTime.of(REMINDER_HOUR, 0)) return emptyList()
    val fromEvents = events.mapNotNull { event ->
        val daysUntil = ChronoUnit.DAYS.between(today, event.date)
        if (daysUntil !in reminderLeadDays(event.typeName)) return@mapNotNull null
        eventReminder(profile, event, daysUntil, now)
    }
    val fromHomework = homework.mapNotNull { item ->
        if (ChronoUnit.DAYS.between(today, item.dueDate) != 1L) return@mapNotNull null
        homeworkReminder(profile, item, now)
    }
    return (fromEvents + fromHomework).distinctBy { it.childKey to it.refKey }
}

internal fun eventReminder(profile: ProfileEntity, event: EventEntity, daysUntil: Long, now: Long): FeedEntryEntity {
    val bring = event.typeName.trim().lowercase() == BRING_TYPE
    val title = when {
        bring -> listOfNotNull("Już jutro: do przyniesienia", event.subjectName).joinToString(" – ")
        daysUntil == 1L -> "Już jutro: " + listOfNotNull(event.typeName, event.subjectName).joinToString(" – ")
        else -> "Już za dwa dni: " + listOfNotNull(event.typeName, event.subjectName).joinToString(" – ")
    }
    return FeedEntryEntity(
        profileId = profile.id,
        childKey = childKey(profile),
        kind = FeedKind.REMINDER,
        refKey = sameThingKey(event.date.toString(), event.typeName, event.subjectName, event.teacherName, event.topic, daysUntil.toString()),
        occurredAt = now,
        title = title,
        detail = event.topic.trim().takeIf { it.isNotEmpty() },
        date = event.date,
    )
}

internal fun homeworkReminder(profile: ProfileEntity, item: HomeworkEntity, now: Long): FeedEntryEntity {
    val detail = listOf(item.topic, item.content).map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
    return FeedEntryEntity(
        profileId = profile.id,
        childKey = childKey(profile),
        kind = FeedKind.HOMEWORK_REMINDER,
        refKey = sameThingKey(item.dueDate.toString(), item.subjectName, item.teacherName, item.topic, item.content),
        occurredAt = now,
        title = listOfNotNull("Już jutro: zadanie domowe", item.subjectName).joinToString(" – "),
        detail = detail.ifEmpty { null },
        date = item.dueDate,
    )
}

/**
 * A short key for "the same thing": a digest of the fields that identify the record, lowercased
 * and trimmed. Identical for the parent and the student view of one event.
 */
internal fun sameThingKey(vararg parts: String?): String {
    val text = parts.joinToString("|") { it.orEmpty().trim().lowercase() }
    val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
    return digest.take(8).joinToString("") { "%02x".format(it) }
}

/**
 * Sends the reminders due now, one notification per child. Runs at [REMINDER_HOUR] from a
 * worker and right after every sync, so items that appear later in the day still go out.
 */
class SchoolReminders(
    private val context: Context,
    private val db: BrulionDatabase,
    private val accounts: AccountRepository,
    private val settings: Settings,
) {

    suspend fun check(now: ZonedDateTime = ZonedDateTime.now(SCHOOL_ZONE)) {
        if (!settings.remindersEnabled.value) return
        val today = now.toLocalDate()
        val time = now.toLocalTime()
        val tomorrow = today.plusDays(1)
        val nowEpoch = nowSeconds()
        val fresh = mutableListOf<Pair<ProfileEntity, FeedEntryEntity>>()
        for (profile in accounts.allProfiles.first()) {
            val events = db.eventDao().from(profile.id, tomorrow)
            val homework = db.homeworkDao().dueBetween(profile.id, tomorrow, tomorrow)
            for (entry in dueReminders(profile, events, homework, today, time, nowEpoch)) {
                // insert returns -1 when the same reminder was already stored, e.g. through the student's profile.
                if (db.feedDao().insert(entry) > 0) fresh += profile to entry
            }
        }
        if (fresh.isEmpty() || !canNotify()) return
        for ((child, items) in fresh.groupBy { it.second.childKey }) {
            val profile = items.first().first
            show(child, profile.id, profile.studentName.substringBefore(' '), items.map { it.second })
        }
    }

    private fun show(child: String, profileId: Long, name: String, entries: List<FeedEntryEntity>) {
        createChannel()
        val open = MainActivity.openIntent(context, "reminders", child)
            .putExtra(MainActivity.EXTRA_PROFILE_ID, profileId)
            .putExtra(MainActivity.EXTRA_OPEN_FEED, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(context, child.hashCode(), open, FLAGS)
        val lines = entries.map { listOfNotNull(it.title, it.detail?.lineSequence()?.firstOrNull()?.takeIf { d -> d.isNotBlank() }).joinToString(" · ") }
        val single = entries.singleOrNull()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (single != null) "$name · ${single.title}" else context.getString(R.string.reminders_title, name, entries.size))
            .setContentText(single?.detail?.lineSequence()?.firstOrNull() ?: lines.first())
            .setStyle(NotificationCompat.InboxStyle().also { style -> lines.take(MAX_LINES).forEach(style::addLine) })
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_TAG, child.hashCode(), notification)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call; the entries are in the feed anyway.
        }
    }

    private fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.reminders_channel), NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = context.getString(R.string.reminders_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "reminders"
        const val NOTIFICATION_TAG = "reminders"
        const val MAX_LINES = 6
        const val FLAGS = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    }
}
