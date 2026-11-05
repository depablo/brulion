package pl.brulion.data.school

import pl.brulion.data.db.LessonState
import pl.brulion.data.db.TimetableLessonEntity

/**
 * The timetable as it was set, without the changes of the week: a substitution shows the planned
 * subject and teacher, a lesson moved away from a slot is back in it, and a lesson moved in from
 * another day is left out (it belongs to its original day).
 */
fun regularPlan(lessons: List<TimetableLessonEntity>): List<TimetableLessonEntity> =
    lessons.filter { it.state != LessonState.MOVED_HERE }.map { lesson ->
        when (lesson.state) {
            LessonState.NORMAL -> lesson
            LessonState.SUBSTITUTION -> lesson.copy(
                state = LessonState.NORMAL,
                subjectName = lesson.originalSubjectName ?: lesson.subjectName,
                teacherName = lesson.originalTeacherName ?: lesson.teacherName,
            )
            LessonState.CANCELLED, LessonState.MOVED_AWAY -> lesson.copy(state = LessonState.NORMAL)
            LessonState.MOVED_HERE -> lesson
        }
    }
