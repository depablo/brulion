package pl.brulion.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

// Every synced record belongs to a profile and disappears with it (ForeignKey.CASCADE).
// firstSeenAt is when Brulion first saw the record (epoch seconds); 0 means "existed before the first sync".
// The daily summary is built from records whose firstSeenAt falls after the previous summary.

enum class GradeKind { NORMAL, POINT, DESCRIPTIVE }

enum class GradeType { REGULAR, SEMESTER_PROPOSED, SEMESTER_FINAL, YEAR_PROPOSED, YEAR_FINAL }

@Entity(
    tableName = "grades",
    primaryKeys = ["profileId", "kind", "id"],
    foreignKeys = [ForeignKey(ProfileEntity::class, ["id"], ["profileId"], onDelete = ForeignKey.CASCADE)],
)
data class GradeEntity(
    val profileId: Long,
    val kind: GradeKind,
    val id: Long,
    val type: GradeType,
    val semester: Int,
    val subjectId: Long,
    val subjectName: String,
    /** As shown in Librus: "5+", "np", "12/15" etc. */
    val value: String,
    /** Numeric value for averages: "5+" = 5.5, "5-" = 4.75; null when it has none. */
    val numericValue: Double?,
    /** Max points for point grades. */
    val maxValue: Double?,
    /** 0 when the grade doesn't count towards the average. */
    val weight: Double,
    val categoryName: String,
    val color: Int,
    val comment: String?,
    val teacherName: String?,
    val addedAt: Long,
    /** Id of the grade that corrects this one, if any. */
    val improvedById: Long?,
    val firstSeenAt: Long,
)

@Entity(
    tableName = "announcements",
    primaryKeys = ["profileId", "id"],
    foreignKeys = [ForeignKey(ProfileEntity::class, ["id"], ["profileId"], onDelete = ForeignKey.CASCADE)],
)
data class AnnouncementEntity(
    val profileId: Long,
    /** Librus uses string ids here, also needed to mark the announcement as read. */
    val id: String,
    val subject: String,
    val content: String,
    val authorName: String?,
    val startDate: LocalDate?,
    val endDate: LocalDate?,
    val read: Boolean,
    val addedAt: Long,
    val firstSeenAt: Long,
)

/** Announcements the user dismissed; ids are school-wide, so one entry covers every child. */
@Entity(tableName = "hidden_announcements")
data class HiddenAnnouncementEntity(
    @PrimaryKey val id: String,
    val hiddenAt: Long,
)

/** A planner entry Brulion put into the phone's calendar; [fingerprint] tells when to update it. */
@Entity(tableName = "calendar_links")
data class CalendarLinkEntity(
    @PrimaryKey val key: String,
    val calendarEventId: Long,
    val date: LocalDate,
    val fingerprint: String,
)

enum class AttendanceCategory { PRESENT, ABSENT, EXCUSED, LATE, RELEASED, OTHER }

@Entity(
    tableName = "attendance",
    primaryKeys = ["profileId", "id"],
    foreignKeys = [ForeignKey(ProfileEntity::class, ["id"], ["profileId"], onDelete = ForeignKey.CASCADE)],
)
data class AttendanceEntity(
    val profileId: Long,
    /** String, because Librus mixes ids like "123" and "t123". */
    val id: String,
    val date: LocalDate,
    val lessonNo: Int?,
    val semester: Int,
    val category: AttendanceCategory,
    val typeName: String,
    val typeShort: String,
    val typeColor: Int?,
    val subjectName: String?,
    val teacherName: String?,
    val addedAt: Long,
    val firstSeenAt: Long,
)

@Entity(
    tableName = "events",
    primaryKeys = ["profileId", "id"],
    foreignKeys = [ForeignKey(ProfileEntity::class, ["id"], ["profileId"], onDelete = ForeignKey.CASCADE)],
)
data class EventEntity(
    val profileId: Long,
    val id: Long,
    val date: LocalDate,
    val lessonNo: Int?,
    val startTime: String?,
    val typeName: String,
    val color: Int,
    val subjectName: String?,
    val teacherName: String?,
    val topic: String,
    val addedAt: Long,
    val firstSeenAt: Long,
)

enum class NotePolarity { POSITIVE, NEGATIVE, NEUTRAL }

/** Behaviour notes ("uwagi"). */
@Entity(
    tableName = "notes",
    primaryKeys = ["profileId", "id"],
    foreignKeys = [ForeignKey(ProfileEntity::class, ["id"], ["profileId"], onDelete = ForeignKey.CASCADE)],
)
data class NoteEntity(
    val profileId: Long,
    val id: Long,
    val date: LocalDate,
    val text: String,
    val categoryName: String?,
    val teacherName: String?,
    val polarity: NotePolarity,
    val firstSeenAt: Long,
)

@Entity(
    tableName = "lucky_numbers",
    primaryKeys = ["profileId", "date"],
    foreignKeys = [ForeignKey(ProfileEntity::class, ["id"], ["profileId"], onDelete = ForeignKey.CASCADE)],
)
data class LuckyNumberEntity(
    val profileId: Long,
    val date: LocalDate,
    val number: Int,
)

enum class LessonState {
    NORMAL,
    CANCELLED,
    /** Different teacher/subject in the same slot. */
    SUBSTITUTION,
    /** The lesson was moved away from this slot to [TimetableLessonEntity.otherDate]. */
    MOVED_AWAY,
    /** The lesson was moved into this slot from [TimetableLessonEntity.otherDate]. */
    MOVED_HERE,
}

@Entity(
    tableName = "timetable",
    primaryKeys = ["profileId", "date", "lessonNo", "position"],
    foreignKeys = [ForeignKey(ProfileEntity::class, ["id"], ["profileId"], onDelete = ForeignKey.CASCADE)],
)
data class TimetableLessonEntity(
    val profileId: Long,
    val date: LocalDate,
    val lessonNo: Int,
    /** Several lessons can share a slot (e.g. groups); this keeps them apart. */
    val position: Int,
    val startTime: String?,
    val endTime: String?,
    val subjectName: String?,
    val teacherName: String?,
    val classroom: String?,
    val state: LessonState,
    /** For substitutions: what was originally planned. */
    val originalSubjectName: String?,
    val originalTeacherName: String?,
    /** For moved lessons: the other end of the move. */
    val otherDate: LocalDate?,
    val otherLessonNo: Int?,
)

@Entity(
    tableName = "messages",
    primaryKeys = ["profileId", "id"],
    foreignKeys = [ForeignKey(ProfileEntity::class, ["id"], ["profileId"], onDelete = ForeignKey.CASCADE)],
)
data class MessageEntity(
    val profileId: Long,
    val id: Long,
    val senderName: String,
    val subject: String,
    /** Start of the message body as returned by the inbox list (plain text). */
    val preview: String,
    val sentAt: Long,
    /** Null while the message is unread. */
    val readAt: Long?,
    val hasAttachments: Boolean,
    val firstSeenAt: Long,
    /** Generated by Synergia (e.g. "a note was added"); not meant to be answered. */
    @ColumnInfo(defaultValue = "0") val automatic: Boolean = false,
)

/**
 * Full content of a message opened in the app. Kept apart from [MessageEntity], which
 * every sync rewrites, so opened messages stay readable offline.
 */
@Entity(
    tableName = "message_bodies",
    primaryKeys = ["profileId", "messageId"],
    foreignKeys = [ForeignKey(ProfileEntity::class, ["id"], ["profileId"], onDelete = ForeignKey.CASCADE)],
)
data class MessageBodyEntity(
    val profileId: Long,
    val messageId: Long,
    val body: String,
    val canReply: Boolean,
    val fetchedAt: Long,
    /** How many people got the message; 1 means it was written to this account only. */
    val receiversCount: Int? = null,
)

@Entity(
    tableName = "message_attachments",
    primaryKeys = ["profileId", "messageId", "id"],
    foreignKeys = [ForeignKey(ProfileEntity::class, ["id"], ["profileId"], onDelete = ForeignKey.CASCADE)],
)
data class MessageAttachmentEntity(
    val profileId: Long,
    val messageId: Long,
    val id: Long,
    val fileName: String,
)

/** Stored by name; [ABSENCE_REMINDER] is written by Brulion itself, not taken from Librus. */
enum class FeedKind { GRADE, NOTE, ATTENDANCE, EVENT, ANNOUNCEMENT, MESSAGE, LESSON_CHANGE, ABSENCE_REMINDER }

/**
 * One line of the activity feed. Written during sync and never replaced by it, so the feed
 * keeps its history even when Librus drops or changes the source record.
 */
@Entity(
    tableName = "feed",
    foreignKeys = [ForeignKey(ProfileEntity::class, ["id"], ["profileId"], onDelete = ForeignKey.CASCADE)],
    indices = [
        Index(value = ["childKey", "kind", "refKey"], unique = true),
        Index("profileId"),
        Index("occurredAt"),
    ],
)
data class FeedEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    /** The same child seen through a parent and a student account gets one entry. */
    val childKey: String,
    val kind: FeedKind,
    /** Identifies the source record within [kind], e.g. a grade or message id. */
    val refKey: String,
    /** When it happened in Librus (or when we noticed, if Librus doesn't say). */
    val occurredAt: Long,
    val title: String,
    val detail: String?,
    /** Message id to open, for [FeedKind.MESSAGE]. */
    val targetId: Long? = null,
    /** Day of a calendar entry or lesson change. */
    val date: LocalDate? = null,
)

/**
 * When a sync section last succeeded for a profile. A section that never succeeded treats
 * everything it gets as already known, so a first sync isn't reported as news.
 */
@Entity(
    tableName = "section_syncs",
    primaryKeys = ["profileId", "section"],
    foreignKeys = [ForeignKey(ProfileEntity::class, ["id"], ["profileId"], onDelete = ForeignKey.CASCADE)],
)
data class SectionSyncEntity(
    val profileId: Long,
    /** A [pl.brulion.data.sync.SyncSection] name. */
    val section: String,
    /** Epoch seconds. */
    val lastOkAt: Long,
)
