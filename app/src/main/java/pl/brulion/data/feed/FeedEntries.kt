package pl.brulion.data.feed

import pl.brulion.data.childKey
import pl.brulion.data.db.AnnouncementEntity
import pl.brulion.data.db.AttendanceCategory
import pl.brulion.data.db.AttendanceEntity
import pl.brulion.data.db.EventEntity
import pl.brulion.data.db.FeedEntryEntity
import pl.brulion.data.db.FeedKind
import pl.brulion.data.db.GradeEntity
import pl.brulion.data.db.GradeType
import pl.brulion.data.db.LessonState
import pl.brulion.data.db.MessageEntity
import pl.brulion.data.db.NoteEntity
import pl.brulion.data.db.NotePolarity
import pl.brulion.data.db.ProfileEntity
import pl.brulion.data.db.TimetableLessonEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields

// Builders for feed lines. The text is final Polish copy, so the feed reads like a diary:
// "Nowa ocena: Matematyka – 5", "Sprawdzian – Biologia, 3.11".

private val ShortDate = DateTimeFormatter.ofPattern("d.MM")

internal fun GradeEntity.toFeed(profile: ProfileEntity) = FeedEntryEntity(
    profileId = profile.id,
    childKey = childKey(profile),
    kind = FeedKind.GRADE,
    refKey = "$kind:$id",
    occurredAt = addedAt,
    title = when (type) {
        GradeType.REGULAR -> "Nowa ocena: $subjectName – $value"
        GradeType.SEMESTER_PROPOSED -> "Przewidywana ocena śródroczna: $subjectName – $value"
        GradeType.SEMESTER_FINAL -> "Ocena śródroczna: $subjectName – $value"
        GradeType.YEAR_PROPOSED -> "Przewidywana ocena roczna: $subjectName – $value"
        GradeType.YEAR_FINAL -> "Ocena roczna: $subjectName – $value"
    },
    detail = listOfNotNull(categoryName.takeIf { it.isNotBlank() }, comment?.takeIf { it.isNotBlank() }).joinToString(" · ")
        .ifEmpty { null },
)

internal fun NoteEntity.toFeed(profile: ProfileEntity, noticedAt: Long) = FeedEntryEntity(
    profileId = profile.id,
    childKey = childKey(profile),
    kind = FeedKind.NOTE,
    refKey = id.toString(),
    occurredAt = noticedAt,
    title = when (polarity) {
        NotePolarity.POSITIVE -> "Nowa uwaga pozytywna"
        NotePolarity.NEGATIVE -> "Nowa uwaga negatywna"
        NotePolarity.NEUTRAL -> "Nowa uwaga"
    },
    detail = text,
)

internal fun AttendanceEntity.toFeed(profile: ProfileEntity): FeedEntryEntity? {
    val what = when (category) {
        AttendanceCategory.PRESENT -> return null
        AttendanceCategory.ABSENT -> "Nieobecność"
        AttendanceCategory.EXCUSED -> "Usprawiedliwiona nieobecność"
        AttendanceCategory.LATE -> "Spóźnienie"
        AttendanceCategory.RELEASED -> "Zwolnienie"
        AttendanceCategory.OTHER -> typeName
    }
    return FeedEntryEntity(
        profileId = profile.id,
        childKey = childKey(profile),
        kind = FeedKind.ATTENDANCE,
        // The id stays when an absence gets excused, so include the category to log that too.
        refKey = "$id:$category",
        occurredAt = addedAt,
        title = "$what: " + listOfNotNull(subjectName, lessonNo?.let { "lekcja $it" }).joinToString(", ") + ", ${date.format(ShortDate)}",
        detail = null,
        date = date,
    )
}

internal fun EventEntity.toFeed(profile: ProfileEntity) = FeedEntryEntity(
    profileId = profile.id,
    childKey = childKey(profile),
    kind = FeedKind.EVENT,
    refKey = id.toString(),
    occurredAt = addedAt,
    title = listOfNotNull(typeName, subjectName).joinToString(" – ") + ", ${date.format(ShortDate)}",
    detail = topic.takeIf { it.isNotBlank() },
    date = date,
)

internal fun AnnouncementEntity.toFeed(profile: ProfileEntity) = FeedEntryEntity(
    profileId = profile.id,
    childKey = childKey(profile),
    kind = FeedKind.ANNOUNCEMENT,
    refKey = id,
    occurredAt = addedAt,
    title = "Ogłoszenie: $subject",
    detail = authorName,
)

internal fun MessageEntity.toFeed(profile: ProfileEntity) = FeedEntryEntity(
    profileId = profile.id,
    childKey = childKey(profile),
    kind = FeedKind.MESSAGE,
    // Each account has its own inbox, so parent and student messages are different entries.
    refKey = "${profile.id}:$id",
    occurredAt = sentAt,
    title = "Wiadomość od $senderName",
    detail = subject,
    targetId = id,
)

internal fun TimetableLessonEntity.toFeed(profile: ProfileEntity, noticedAt: Long): FeedEntryEntity? {
    val subject = subjectName ?: originalSubjectName
    val what = when (state) {
        LessonState.NORMAL -> return null
        LessonState.CANCELLED -> "Odwołana lekcja"
        LessonState.SUBSTITUTION -> "Zastępstwo"
        LessonState.MOVED_AWAY -> "Przeniesiona lekcja"
        LessonState.MOVED_HERE -> "Lekcja przeniesiona na"
    }
    return FeedEntryEntity(
        profileId = profile.id,
        childKey = childKey(profile),
        kind = FeedKind.LESSON_CHANGE,
        // Everything shown is in the key, so a changed substitution is a new entry and the old one goes.
        refKey = listOf(date, lessonNo, state, subject, teacherName).joinToString(":"),
        occurredAt = noticedAt,
        title = "$what: " + listOfNotNull(subject, "${date.format(ShortDate)}, lekcja $lessonNo").joinToString(", "),
        detail = if (state == LessonState.SUBSTITUTION) teacherName?.let { "Prowadzi: $it" } else null,
        date = date,
    )
}

/** An absence still unexcused this many days later is worth a reminder... */
internal const val ABSENCE_REMINDER_AFTER_DAYS = 7L
/** ...but not forever: older ones are probably past the school's deadline. */
internal const val ABSENCE_REMINDER_UNTIL_DAYS = 30L

/**
 * One reminder per child and week about the absences still not excused, or null when there are
 * none. Brulion can't excuse them, so the entry says where it's done. The week in [refKey]
 * keeps repeated syncs from adding it again.
 */
internal fun absenceReminder(profile: ProfileEntity, attendance: List<AttendanceEntity>, today: LocalDate, now: Long): FeedEntryEntity? {
    val absences = attendance.filter {
        it.category == AttendanceCategory.ABSENT &&
            it.date <= today.minusDays(ABSENCE_REMINDER_AFTER_DAYS) &&
            it.date >= today.minusDays(ABSENCE_REMINDER_UNTIL_DAYS)
    }
    if (absences.isEmpty()) return null
    val days = absences.groupBy { it.date }.toSortedMap().map { (date, lessons) ->
        val subjects = lessons.sortedBy { it.lessonNo }.mapNotNull { it.subjectName }.distinct()
        val what = if (lessons.size <= 2 && subjects.isNotEmpty()) subjects.joinToString(", ") else lessonsCount(lessons.size)
        "${date.format(ShortDate)} ($what)"
    }
    val week = "%d-W%02d".format(today.get(IsoFields.WEEK_BASED_YEAR), today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR))
    return FeedEntryEntity(
        profileId = profile.id,
        childKey = childKey(profile),
        kind = FeedKind.ABSENCE_REMINDER,
        refKey = week,
        occurredAt = now,
        title = "Do usprawiedliwienia: ${lessonsCount(absences.size)}",
        detail = "Usprawiedliwić można w module e-usprawiedliwienia na librus.pl.\n" + days.joinToString(", "),
    )
}

/** "1 lekcja", "3 lekcje", "5 lekcji", "22 lekcje". */
internal fun lessonsCount(n: Int): String {
    val word = when {
        n == 1 -> "lekcja"
        n % 10 in 2..4 && n % 100 !in 12..14 -> "lekcje"
        else -> "lekcji"
    }
    return "$n $word"
}
