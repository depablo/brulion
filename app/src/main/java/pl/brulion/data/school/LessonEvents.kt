package pl.brulion.data.school

import pl.brulion.data.db.EventEntity
import pl.brulion.data.db.LessonState
import pl.brulion.data.db.TimetableLessonEntity

/**
 * Assigns tests and other events of a day to the lessons they'll happen in. The lesson number
 * teachers give is often wrong (an English test "on lesson 1" when English is third), so the
 * subject decides and the number only picks between lessons of the same subject; an event
 * without a subject goes by the number. Events that fit no lesson are left out.
 */
fun eventsByLesson(lessons: List<TimetableLessonEntity>, events: List<EventEntity>): Map<TimetableLessonEntity, List<EventEntity>> {
    val result = mutableMapOf<TimetableLessonEntity, MutableList<EventEntity>>()
    for (event in events) {
        val sameDay = lessons.filter { it.date == event.date }
        val lesson = when (val subject = event.subjectName?.takeIf { it.isNotBlank() }) {
            null -> sameDay.firstOrNull { it.lessonNo == event.lessonNo && it.takesPlace() }
            else -> {
                val candidates = sameDay.filter { it.takesPlace() && it.teaches(subject) }
                candidates.firstOrNull { it.lessonNo == event.lessonNo } ?: candidates.firstOrNull()
            }
        } ?: continue
        result.getOrPut(lesson) { mutableListOf() } += event
    }
    return result
}

private fun TimetableLessonEntity.takesPlace() = state != LessonState.CANCELLED && state != LessonState.MOVED_AWAY

/** "Religia" also matches a lesson named "Religia - formacja". */
private fun TimetableLessonEntity.teaches(subject: String): Boolean {
    val name = (subjectName ?: originalSubjectName ?: return false).trim()
    return name.equals(subject.trim(), ignoreCase = true) || name.startsWith(subject.trim() + " ", ignoreCase = true)
}
