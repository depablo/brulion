package pl.brulion.data.calendar

import pl.brulion.data.childKey
import pl.brulion.data.db.EventEntity
import pl.brulion.data.db.ProfileEntity
import pl.brulion.data.db.TimetableLessonEntity
import pl.brulion.data.school.eventsByLesson
import pl.brulion.librus.SCHOOL_ZONE
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** One planner entry as it goes into the phone calendar. Times are epoch millis. */
data class CalendarEntry(
    val key: String,
    val date: LocalDate,
    val title: String,
    val description: String,
    val start: Long,
    val end: Long,
    val allDay: Boolean,
) {
    val fingerprint: String get() = listOf(title, description, start, end, allDay).joinToString("|")
}

/**
 * Upcoming planner entries of every child, once per child even with a parent and a student
 * profile. The time comes from the lesson the entry was matched to (see [eventsByLesson]);
 * without one the entry is all-day rather than at a possibly wrong hour.
 */
fun calendarEntries(
    profiles: List<ProfileEntity>,
    events: List<EventEntity>,
    lessons: List<TimetableLessonEntity>,
    today: LocalDate,
    zone: ZoneId = SCHOOL_ZONE,
): List<CalendarEntry> {
    val byId = profiles.associateBy { it.id }
    val children = profiles.map(::childKey).distinct()
    // Each child's events against that child's own timetable (from any of its profiles).
    val childOf = profiles.associate { it.id to childKey(it) }
    val lessonsByChild = lessons.groupBy { childOf[it.profileId] }
        .mapValues { (_, list) -> list.distinctBy { Triple(it.date, it.lessonNo, it.position) } }
    val lessonOf = events.groupBy { childOf[it.profileId] }
        .flatMap { (child, childEvents) -> eventsByLesson(lessonsByChild[child].orEmpty(), childEvents).entries }
        .flatMap { (lesson, matched) -> matched.map { (it.profileId to it.id) to lesson } }
        .toMap()
    return events.filter { it.date >= today }
        .mapNotNull { event -> byId[event.profileId]?.let { event to it } }
        .groupBy { (event, profile) -> childKey(profile) + ":" + event.id }
        .map { (key, same) ->
            // The lesson may be known on only one of the child's profiles.
            val (event, profile) = same.firstOrNull { (e, _) -> (e.profileId to e.id) in lessonOf } ?: same.first()
            val lesson = lessonOf[event.profileId to event.id]
            val name = profile.studentName.substringBefore(' ')
            val title = listOfNotNull(event.typeName, event.subjectName).joinToString(" – ") +
                if (children.size > 1 && name.isNotBlank()) " · $name" else ""
            val description = listOfNotNull(
                event.topic.takeIf { it.isNotBlank() },
                event.teacherName,
                "Z terminarza Librusa, dodane przez Brulion.",
            ).joinToString("\n")
            val start = lesson?.startTime?.let(LocalTime::parse)
            val end = lesson?.endTime?.let(LocalTime::parse)
            if (start != null && end != null) {
                CalendarEntry(
                    key = key, date = event.date, title = title, description = description,
                    start = event.date.atTime(start).atZone(zone).toInstant().toEpochMilli(),
                    end = event.date.atTime(end).atZone(zone).toInstant().toEpochMilli(),
                    allDay = false,
                )
            } else {
                // Calendar providers want all-day entries at UTC midnight.
                val midnight = event.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
                CalendarEntry(key, event.date, title, description, midnight, midnight + DAY_MILLIS, allDay = true)
            }
        }
}

private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
