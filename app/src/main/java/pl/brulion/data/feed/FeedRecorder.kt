package pl.brulion.data.feed

import androidx.room.withTransaction
import kotlinx.coroutines.flow.first
import pl.brulion.data.Announcements
import pl.brulion.data.childKey
import pl.brulion.data.db.BrulionDatabase
import pl.brulion.data.db.FeedEntryEntity
import pl.brulion.data.db.FeedKind
import pl.brulion.data.db.LessonState
import pl.brulion.data.db.ProfileEntity
import pl.brulion.librus.SCHOOL_ZONE
import pl.brulion.librus.nowSeconds
import pl.brulion.librus.schoolToday
import java.time.LocalDate

/**
 * Adds recent records of a profile to the feed after a sync. Anything from the last two weeks
 * qualifies, so the very first sync already fills the feed; the unique index keeps entries
 * that were logged before from being added again. Lesson changes are the exception: they
 * follow the current timetable.
 */
class FeedRecorder(private val db: BrulionDatabase, private val announcements: Announcements) {

    suspend fun record(profile: ProfileEntity) {
        val now = nowSeconds()
        val since = now - WINDOW_SECONDS
        val today = schoolToday()
        val id = profile.id

        val entries = buildList<FeedEntryEntity> {
            db.gradeDao().addedSince(id, since).mapTo(this) { it.toFeed(profile) }
            // Notes carry only a date; one seen before stamps existed counts at noon that day.
            db.noteDao().recent(id, since, sinceDate = today.minusDays(WINDOW_DAYS)).forEach { note ->
                val at = if (note.firstSeenAt > 0) note.firstSeenAt else note.date.noonEpoch()
                if (at >= since) add(note.toFeed(profile, at))
            }
            db.attendanceDao().addedSince(id, since).mapNotNullTo(this) { it.toFeed(profile) }
            db.eventDao().addedSince(id, since).mapTo(this) { it.toFeed(profile) }
            announcements.visible(id).filter { it.addedAt >= since }.mapTo(this) { it.toFeed(profile) }
            db.messageDao().sentSince(id, since).mapTo(this) { it.toFeed(profile) }
        }
        // Lesson changes describe the current plan, not past events: one the school took back
        // or changed again leaves the feed.
        val lessons = db.timetableDao().observeRange(id, today, today.plusWeeks(2)).first()
        val lessonChanges = lessons.filter { it.state != LessonState.NORMAL }.mapNotNull { it.toFeed(profile, now) }
        db.withTransaction {
            db.feedDao().insertAll(entries + lessonChanges)
            // A profile without a timetable (not fetched yet) knows nothing about the changes.
            if (lessons.isNotEmpty()) {
                val keys = lessonChanges.map { it.refKey }
                db.feedDao().deleteOutdated(childKey(profile), FeedKind.LESSON_CHANGE, today, today.plusWeeks(2), keys)
            }
        }
        db.feedDao().deleteOlderThan(now - RETENTION_SECONDS)
    }

    private fun LocalDate.noonEpoch(): Long = atTime(12, 0).atZone(SCHOOL_ZONE).toEpochSecond()

    private companion object {
        const val WINDOW_DAYS = 14L
        const val WINDOW_SECONDS = WINDOW_DAYS * 24 * 60 * 60
        const val RETENTION_SECONDS = 120 * 24 * 60 * 60L
    }
}
