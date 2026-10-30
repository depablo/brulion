/*
 * Part of Brulion, GNU GPL v3. Based on Librus support in Szkolny.eu
 * (https://github.com/szkolny-eu/szkolny-android), (c) Kuba Szczodrzyński and contributors, GPL v3.
 */
package pl.brulion.data.sync

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import pl.brulion.data.db.LessonState
import pl.brulion.data.db.TimetableLessonEntity
import pl.brulion.librus.flag
import pl.brulion.librus.int
import pl.brulion.librus.obj
import pl.brulion.librus.objects
import pl.brulion.librus.parseLibrusDate
import pl.brulion.librus.parseLibrusTime
import pl.brulion.librus.refId
import pl.brulion.librus.string
import java.time.LocalDate

internal class TimetableParser(private val session: SyncSession, private val profileId: Long) {

    /** Lessons left out because Librus gave no lesson number (the number is part of the key). */
    var skipped = 0
        private set

    /** Lessons of the week starting on [monday]. */
    suspend fun parseWeek(monday: LocalDate): List<TimetableLessonEntity> {
        val subjects = session.subjects()
        val teachers = session.teachers()
        // Classrooms sometimes fails with HTTP 500; the timetable is still useful without them.
        val classrooms = runCatching { session.classrooms() }.getOrDefault(emptyMap())
        val days = session.get("Timetables?weekStart=$monday").obj("Timetable") ?: return emptyList()

        return days.entries.flatMap { (dateKey, slots) ->
            val date = parseLibrusDate(dateKey) ?: return@flatMap emptyList()
            (slots as? JsonArray).orEmpty().filterIsInstance<JsonArray>().flatMap { slot ->
                slot.objects().mapIndexedNotNull { position, lesson ->
                    parseLesson(date, position, lesson, subjects, teachers, classrooms)
                }
            }
        }
    }

    private fun parseLesson(
        date: LocalDate,
        position: Int,
        lesson: JsonObject,
        subjects: Map<Long, String>,
        teachers: Map<Long, String>,
        classrooms: Map<Long, String>,
    ): TimetableLessonEntity? {
        val lessonNo = lesson.int("LessonNo") ?: run {
            skipped++
            return null
        }
        val substitution = lesson.flag("IsSubstitutionClass")
        val cancelled = lesson.flag("IsCanceled")
        val originalDate = parseLibrusDate(lesson.string("OrgDate"))
        val originalLessonNo = lesson.int("OrgLessonNo")

        // Librus encodes moves as a cancelled substitution (source) plus a substitution elsewhere (target).
        val state = when {
            substitution && cancelled -> LessonState.MOVED_AWAY
            substitution && (originalDate == null || (originalDate == date && originalLessonNo == lessonNo)) ->
                LessonState.SUBSTITUTION
            substitution -> LessonState.MOVED_HERE
            cancelled -> LessonState.CANCELLED
            else -> LessonState.NORMAL
        }
        val (otherDate, otherLessonNo) = when (state) {
            LessonState.MOVED_AWAY -> parseLibrusDate(lesson.string("NewDate")) to lesson.int("NewLessonNo")
            LessonState.MOVED_HERE -> originalDate to originalLessonNo
            else -> null to null
        }
        val isSubstitution = state == LessonState.SUBSTITUTION

        return TimetableLessonEntity(
            profileId = profileId,
            date = date,
            lessonNo = lessonNo,
            position = position,
            startTime = parseLibrusTime(lesson.string("HourFrom")),
            endTime = parseLibrusTime(lesson.string("HourTo")),
            subjectName = lesson.refId("Subject")?.let(subjects::get),
            teacherName = lesson.refId("Teacher")?.let(teachers::get),
            classroom = lesson.refId("Classroom")?.let(classrooms::get),
            state = state,
            originalSubjectName = if (isSubstitution) lesson.refId("OrgSubject")?.let(subjects::get) else null,
            originalTeacherName = if (isSubstitution) lesson.refId("OrgTeacher")?.let(teachers::get) else null,
            otherDate = otherDate,
            otherLessonNo = otherLessonNo,
        )
    }
}
