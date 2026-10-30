package pl.brulion.data.summary

import kotlinx.coroutines.flow.first
import pl.brulion.data.db.AttendanceCategory
import pl.brulion.data.Announcements
import pl.brulion.data.db.BrulionDatabase
import pl.brulion.data.db.LessonState
import pl.brulion.data.db.ProfileEntity
import pl.brulion.data.school.TIMETABLE_WEEKS
import java.time.LocalDate

/**
 * Collects what's new for one child. A child can have several profiles (parent and student
 * account); school data comes from the first one, unread messages from all of them,
 * because each account has its own inbox.
 */
class SummaryRepository(private val db: BrulionDatabase, private val announcements: Announcements) {

    suspend fun input(profiles: List<ProfileEntity>, since: Long, today: LocalDate): SummaryInput {
        val primary = profiles.first().id
        fun Long.isNew() = this > since

        val events = db.eventDao().from(primary, today)
        val lessons = db.timetableDao().observeRange(primary, today.plusDays(1), today.plusWeeks(TIMETABLE_WEEKS)).first()
        val nextDay = lessons.minOfOrNull { it.date }
        val nextDayEvents = if (nextDay != null) events.filter { it.date == nextDay } else emptyList()

        return SummaryInput(
            studentName = profiles.first().studentName,
            newGrades = db.gradeDao().seenAfter(primary, since),
            newNotes = db.noteDao().seenAfter(primary, since),
            newAbsences = db.attendanceDao().seenAfter(primary, since).filter { it.category != AttendanceCategory.PRESENT },
            newAnnouncements = announcements.visible(primary).filter { it.firstSeenAt.isNew() },
            unreadMessages = profiles.flatMap { profile -> db.messageDao().observeUnread(profile.id).first() },
            newEvents = events.filter { it.firstSeenAt.isNew() && it.date >= today && it !in nextDayEvents },
            nextDay = nextDay,
            nextDayEvents = nextDayEvents,
            nextDayChanges = lessons.filter { it.date == nextDay && it.state != LessonState.NORMAL },
            nextDayLuckyNumber = nextDay?.let { db.luckyNumberDao().observeFrom(primary, it).first() },
        )
    }
}
