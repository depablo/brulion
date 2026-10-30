package pl.brulion.data.school

import pl.brulion.data.db.LessonState
import pl.brulion.data.db.TimetableLessonEntity
import pl.brulion.librus.schoolTimeNow
import pl.brulion.librus.schoolToday
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Monday of the week the timetable starts with; on weekends that's already the coming week. */
fun timetableWeekStart(today: LocalDate = schoolToday()): LocalDate =
    if (today.dayOfWeek >= DayOfWeek.SATURDAY) {
        today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
    } else {
        today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    }

/** Number of synced timetable weeks (current and next). */
const val TIMETABLE_WEEKS = 2L

/** Today while lessons are still ahead, otherwise the next day with lessons. */
fun nextLessonDay(
    lessons: List<TimetableLessonEntity>,
    today: LocalDate = schoolToday(),
    now: String = schoolTimeNow(),
): Pair<LocalDate, List<TimetableLessonEntity>>? =
    lessons.groupBy { it.date }.toSortedMap().entries.firstOrNull { (day, dayLessons) ->
        day > today || (day == today && dayLessons.any { it.endTime == null || it.endTime > now })
    }?.toPair()

/** First start and last end of the lessons that take place; null when none do. */
fun schoolHours(dayLessons: List<TimetableLessonEntity>): Pair<String, String>? {
    val held = dayLessons.filter { it.state != LessonState.CANCELLED && it.state != LessonState.MOVED_AWAY }
    val start = held.mapNotNull { it.startTime }.minOrNull() ?: return null
    val end = held.mapNotNull { it.endTime }.maxOrNull() ?: return null
    return start to end
}
