/*
 * Part of Brulion, GNU GPL v3. Based on Librus support in Szkolny.eu
 * (https://github.com/szkolny-eu/szkolny-android), (c) Kuba Szczodrzyński and contributors, GPL v3.
 */
package pl.brulion.data.sync

import pl.brulion.data.db.AnnouncementEntity
import pl.brulion.data.db.AttendanceCategory
import pl.brulion.data.db.AttendanceEntity
import pl.brulion.data.db.EventEntity
import pl.brulion.data.db.LuckyNumberEntity
import pl.brulion.data.db.NoteEntity
import pl.brulion.data.db.NotePolarity
import pl.brulion.librus.array
import pl.brulion.librus.bool
import pl.brulion.librus.int
import pl.brulion.librus.librusColor
import pl.brulion.librus.long
import pl.brulion.librus.normalizeSpaces
import pl.brulion.librus.obj
import pl.brulion.librus.objects
import pl.brulion.librus.parseLibrusDate
import pl.brulion.librus.parseLibrusDateTime
import pl.brulion.librus.parseLibrusTime
import pl.brulion.librus.refId
import pl.brulion.librus.schoolToday
import pl.brulion.librus.string

internal class AnnouncementParser(private val session: SyncSession, private val profileId: Long) {
    suspend fun parse(): List<AnnouncementEntity> {
        val teachers = session.teachers()
        return session.get("SchoolNotices").array("SchoolNotices")?.objects().orEmpty().mapNotNull { notice ->
            AnnouncementEntity(
                profileId = profileId,
                id = notice.string("Id") ?: return@mapNotNull null,
                subject = notice.string("Subject").orEmpty().trim(),
                content = notice.string("Content").orEmpty().trim(),
                authorName = notice.refId("AddedBy")?.let(teachers::get),
                startDate = parseLibrusDate(notice.string("StartDate")),
                endDate = parseLibrusDate(notice.string("EndDate")),
                read = notice.bool("WasRead") == true,
                addedAt = parseLibrusDateTime(notice.string("CreationDate")) ?: 0,
                firstSeenAt = 0,
            )
        }
    }
}

internal class AttendanceParser(private val session: SyncSession, private val profileId: Long) {

    private class Type(val category: AttendanceCategory, val name: String, val short: String, val color: Int?)

    suspend fun parse(): List<AttendanceEntity> {
        val subjects = session.subjects()
        val teachers = session.teachers()
        val lessons = session.lessons()
        val types = session.byId("Attendances/Types", "Types").mapValues { (id, type) ->
            // Custom types point at the standard type they behave like.
            val standardId = type.refId("StandardType") ?: id
            val category = when (standardId) {
                1L -> AttendanceCategory.ABSENT
                2L -> AttendanceCategory.LATE
                3L -> AttendanceCategory.EXCUSED
                4L -> AttendanceCategory.RELEASED
                100L -> AttendanceCategory.PRESENT
                else -> AttendanceCategory.OTHER
            }
            Type(
                category = category,
                name = type.string("Name").orEmpty().trim(),
                short = type.string("Short").orEmpty().trim(),
                color = type.string("ColorRGB")?.let { rgb -> rgb.toLongOrNull(16)?.let { (0xFF000000 or it).toInt() } },
            )
        }

        return session.get("Attendances").array("Attendances")?.objects().orEmpty().mapNotNull { item ->
            val id = item.string("Id") ?: return@mapNotNull null
            val date = parseLibrusDate(item.string("Date")) ?: return@mapNotNull null
            val type = item.refId("Type")?.let(types::get)
            val lesson = item.refId("Lesson")?.let(lessons::get)
            AttendanceEntity(
                profileId = profileId,
                id = id,
                date = date,
                lessonNo = item.int("LessonNo"),
                semester = item.int("Semester") ?: 0,
                category = type?.category ?: AttendanceCategory.OTHER,
                typeName = type?.name ?: "nieznany rodzaj",
                typeShort = type?.short ?: "?",
                typeColor = type?.color,
                subjectName = lesson?.first?.let(subjects::get),
                teacherName = (item.refId("AddedBy") ?: lesson?.second)?.let(teachers::get),
                addedAt = parseLibrusDateTime(item.string("AddDate")) ?: 0,
                firstSeenAt = 0,
            )
        }
    }
}

internal class EventParser(private val session: SyncSession, private val profileId: Long) {
    suspend fun parse(): List<EventEntity> {
        val subjects = session.subjects()
        val teachers = session.teachers()
        val types = session.byId("HomeWorks/Categories", "Categories")
        val startTimes = runCatching { session.lessonStartTimes() }.getOrDefault(emptyMap())

        return session.get("HomeWorks").array("HomeWorks")?.objects().orEmpty().mapNotNull { event ->
            val id = event.long("Id") ?: return@mapNotNull null
            val date = parseLibrusDate(event.string("Date")) ?: return@mapNotNull null
            val type = event.refId("Category")?.let(types::get)
            val lessonNo = event.int("LessonNo")
            val onlineUrl = event.obj("onlineLessonUrl")?.string("url")
            EventEntity(
                profileId = profileId,
                id = id,
                date = date,
                lessonNo = lessonNo,
                startTime = lessonNo?.let(startTimes::get) ?: parseLibrusTime(event.string("TimeFrom")),
                typeName = type?.string("Name")?.normalizeSpaces().orEmpty(),
                color = librusColor(type?.obj("Color")?.int("Id")),
                subjectName = event.refId("Subject")?.let(subjects::get),
                teacherName = event.refId("CreatedBy")?.let(teachers::get),
                topic = listOfNotNull(event.string("Content")?.trim(), onlineUrl).joinToString("\n"),
                addedAt = parseLibrusDateTime(event.string("AddDate")) ?: 0,
                firstSeenAt = 0,
            )
        }
    }
}

internal class NoteParser(private val session: SyncSession, private val profileId: Long) {
    suspend fun parse(): List<NoteEntity> {
        val teachers = session.teachers()
        val categories = runCatching { session.byId("Notes/Categories", "Categories") }.getOrDefault(emptyMap())

        return session.get("Notes").array("Notes")?.objects().orEmpty().mapNotNull { note ->
            NoteEntity(
                profileId = profileId,
                id = note.long("Id") ?: return@mapNotNull null,
                date = parseLibrusDate(note.string("Date")) ?: return@mapNotNull null,
                text = note.string("Text").orEmpty().trim(),
                categoryName = note.refId("Category")?.let(categories::get)?.string("CategoryName")?.trim(),
                teacherName = note.refId("Teacher")?.let(teachers::get),
                polarity = when (note.int("Positive")) {
                    0 -> NotePolarity.NEGATIVE
                    1 -> NotePolarity.POSITIVE
                    else -> NotePolarity.NEUTRAL
                },
                firstSeenAt = 0,
            )
        }
    }
}

internal class LuckyNumberParser(private val session: SyncSession, private val profileId: Long) {
    suspend fun parse(): LuckyNumberEntity? {
        val lucky = session.get("LuckyNumbers").obj("LuckyNumber") ?: return null
        return LuckyNumberEntity(
            profileId = profileId,
            date = parseLibrusDate(lucky.string("LuckyNumberDay")) ?: schoolToday(),
            number = lucky.int("LuckyNumber") ?: return null,
        )
    }
}
