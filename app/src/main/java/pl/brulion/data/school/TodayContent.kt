package pl.brulion.data.school

import pl.brulion.data.db.AnnouncementEntity
import pl.brulion.data.db.EventEntity
import pl.brulion.data.db.GradeEntity
import pl.brulion.data.db.LessonState
import pl.brulion.data.db.MessageEntity
import pl.brulion.data.db.TimetableLessonEntity
import java.time.LocalDate

/** What the "Dziś" screen shows for one profile. */
data class TodayContent(
    val lessonDay: Pair<LocalDate, List<TimetableLessonEntity>>?,
    val unread: List<MessageEntity>,
    val recentGrades: List<GradeEntity>,
    val upcoming: List<EventEntity>,
    val announcements: List<AnnouncementEntity>,
    val changes: List<TimetableLessonEntity>,
) {
    /**
     * Nothing worth a section. A parent wants to know what happened, a student what's coming,
     * so lessons count only for the student and grades and changes only for the parent.
     */
    fun isQuiet(isParent: Boolean): Boolean =
        unread.isEmpty() && upcoming.isEmpty() && announcements.isEmpty() &&
            (if (isParent) recentGrades.isEmpty() && changes.isEmpty() else lessonDay == null)
}

/**
 * Picks the recent and upcoming bits from the profile's data. [now] is epoch seconds and
 * [nowTime] "HH:mm", both for "today": lessons that are over don't count.
 */
fun todayContent(
    lessons: List<TimetableLessonEntity>,
    messages: List<MessageEntity>,
    grades: List<GradeEntity>,
    events: List<EventEntity>,
    announcements: List<AnnouncementEntity>,
    today: LocalDate,
    now: Long,
    nowTime: String,
): TodayContent = TodayContent(
    lessonDay = nextLessonDay(lessons, today, nowTime),
    unread = messages.filter { it.readAt == null },
    recentGrades = grades.filter { now - it.addedAt < RECENT_SECONDS || (it.firstSeenAt > 0 && now - it.firstSeenAt < DAY_SECONDS) }
        .sortedByDescending { it.addedAt }
        .take(MAX_GRADES),
    upcoming = events.filter { it.date >= today && it.date <= today.plusDays(RECENT_DAYS) }.take(MAX_EVENTS),
    announcements = announcements.filter { now - it.addedAt < RECENT_SECONDS }.take(MAX_ANNOUNCEMENTS),
    changes = lessons.filter { it.state != LessonState.NORMAL && it.date <= today.plusDays(RECENT_DAYS) },
)

/** How far "recent" and "upcoming" reach. */
const val RECENT_DAYS = 7L
private const val DAY_SECONDS = 24 * 60 * 60L
private const val RECENT_SECONDS = RECENT_DAYS * DAY_SECONDS
private const val MAX_GRADES = 8
private const val MAX_EVENTS = 6
private const val MAX_ANNOUNCEMENTS = 3
