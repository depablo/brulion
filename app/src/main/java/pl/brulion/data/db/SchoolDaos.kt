package pl.brulion.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** (key, firstSeenAt) pairs used to keep firstSeenAt stable across syncs. */
data class SeenRow(val key: String, val firstSeenAt: Long)

@Dao
interface GradeDao {
    @Query("SELECT * FROM grades WHERE profileId = :profileId AND addedAt >= :since")
    suspend fun addedSince(profileId: Long, since: Long): List<GradeEntity>

    @Query("SELECT * FROM grades WHERE profileId = :profileId AND firstSeenAt > :since")
    suspend fun seenAfter(profileId: Long, since: Long): List<GradeEntity>

    @Query("SELECT kind || ':' || id AS `key`, firstSeenAt FROM grades WHERE profileId = :profileId")
    suspend fun seen(profileId: Long): List<SeenRow>

    @Query("DELETE FROM grades WHERE profileId = :profileId AND kind = :kind")
    suspend fun deleteKind(profileId: Long, kind: GradeKind)

    @Insert
    suspend fun insertAll(grades: List<GradeEntity>)

    @Transaction
    suspend fun replaceKind(profileId: Long, kind: GradeKind, grades: List<GradeEntity>) {
        deleteKind(profileId, kind)
        insertAll(grades)
    }

    /** Grades added or first seen since the given times, for the "Dziś" screen. */
    @Query("SELECT * FROM grades WHERE profileId = :profileId AND (addedAt >= :addedSince OR firstSeenAt >= :seenSince)")
    fun observeRecent(profileId: Long, addedSince: Long, seenSince: Long): Flow<List<GradeEntity>>

    @Query("SELECT * FROM grades WHERE profileId = :profileId ORDER BY subjectName, addedAt")
    fun observeAll(profileId: Long): Flow<List<GradeEntity>>
}

@Dao
interface AnnouncementDao {
    @Query("SELECT id AS `key`, firstSeenAt FROM announcements WHERE profileId = :profileId")
    suspend fun seen(profileId: Long): List<SeenRow>

    @Query("DELETE FROM announcements WHERE profileId = :profileId")
    suspend fun deleteAll(profileId: Long)

    @Insert
    suspend fun insertAll(items: List<AnnouncementEntity>)

    @Query("SELECT * FROM announcements")
    fun observeEveryProfile(): Flow<List<AnnouncementEntity>>

    @Transaction
    suspend fun replaceAll(profileId: Long, items: List<AnnouncementEntity>) {
        deleteAll(profileId)
        insertAll(items)
    }

    @Query("SELECT * FROM announcements WHERE profileId = :profileId ORDER BY addedAt DESC")
    fun observeAll(profileId: Long): Flow<List<AnnouncementEntity>>
}

@Dao
interface AttendanceDao {
    @Query("SELECT * FROM attendance WHERE profileId = :profileId AND addedAt >= :since")
    suspend fun addedSince(profileId: Long, since: Long): List<AttendanceEntity>

    @Query("SELECT * FROM attendance WHERE profileId = :profileId AND firstSeenAt > :since")
    suspend fun seenAfter(profileId: Long, since: Long): List<AttendanceEntity>

    @Query("SELECT id AS `key`, firstSeenAt FROM attendance WHERE profileId = :profileId")
    suspend fun seen(profileId: Long): List<SeenRow>

    @Query("DELETE FROM attendance WHERE profileId = :profileId")
    suspend fun deleteAll(profileId: Long)

    @Insert
    suspend fun insertAll(items: List<AttendanceEntity>)

    @Transaction
    suspend fun replaceAll(profileId: Long, items: List<AttendanceEntity>) {
        deleteAll(profileId)
        insertAll(items)
    }

    @Query("SELECT * FROM attendance WHERE profileId = :profileId ORDER BY date DESC, lessonNo DESC")
    fun observeAll(profileId: Long): Flow<List<AttendanceEntity>>
}

@Dao
interface EventDao {
    @Query("SELECT * FROM events WHERE profileId = :profileId AND addedAt >= :since")
    suspend fun addedSince(profileId: Long, since: Long): List<EventEntity>

    @Query("SELECT * FROM events WHERE profileId = :profileId AND date >= :from ORDER BY date, lessonNo, startTime")
    suspend fun from(profileId: Long, from: LocalDate): List<EventEntity>

    @Query("SELECT CAST(id AS TEXT) AS `key`, firstSeenAt FROM events WHERE profileId = :profileId")
    suspend fun seen(profileId: Long): List<SeenRow>

    @Query("DELETE FROM events WHERE profileId = :profileId")
    suspend fun deleteAll(profileId: Long)

    @Insert
    suspend fun insertAll(items: List<EventEntity>)

    @Transaction
    suspend fun replaceAll(profileId: Long, items: List<EventEntity>) {
        deleteAll(profileId)
        insertAll(items)
    }

    @Query("SELECT * FROM events WHERE profileId = :profileId ORDER BY date, lessonNo, startTime")
    fun observeAll(profileId: Long): Flow<List<EventEntity>>

    @Query("SELECT * FROM events WHERE profileId = :profileId AND date BETWEEN :from AND :to ORDER BY date, lessonNo, startTime")
    fun observeRange(profileId: Long, from: LocalDate, to: LocalDate): Flow<List<EventEntity>>
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE profileId = :profileId AND firstSeenAt > :since")
    suspend fun seenAfter(profileId: Long, since: Long): List<NoteEntity>

    /** Seen since [since], or, for notes from before stamps existed, dated since [sinceDate]. */
    @Query("SELECT * FROM notes WHERE profileId = :profileId AND (firstSeenAt >= :since OR (firstSeenAt = 0 AND date >= :sinceDate))")
    suspend fun recent(profileId: Long, since: Long, sinceDate: LocalDate): List<NoteEntity>

    @Query("SELECT CAST(id AS TEXT) AS `key`, firstSeenAt FROM notes WHERE profileId = :profileId")
    suspend fun seen(profileId: Long): List<SeenRow>

    @Query("DELETE FROM notes WHERE profileId = :profileId")
    suspend fun deleteAll(profileId: Long)

    @Insert
    suspend fun insertAll(items: List<NoteEntity>)

    @Transaction
    suspend fun replaceAll(profileId: Long, items: List<NoteEntity>) {
        deleteAll(profileId)
        insertAll(items)
    }

    @Query("SELECT * FROM notes WHERE profileId = :profileId ORDER BY date DESC")
    fun observeAll(profileId: Long): Flow<List<NoteEntity>>
}

@Dao
interface MessageDao {
    @Query("SELECT CAST(id AS TEXT) AS `key`, firstSeenAt FROM messages WHERE profileId = :profileId")
    suspend fun seen(profileId: Long): List<SeenRow>

    @Upsert
    suspend fun upsertAll(items: List<MessageEntity>)

    @Query("DELETE FROM messages WHERE profileId = :profileId AND sentAt >= :from AND id NOT IN (:keep)")
    suspend fun deleteMissing(profileId: Long, from: Long, keep: List<Long>)

    @Query("DELETE FROM message_bodies WHERE profileId = :profileId AND messageId NOT IN (SELECT id FROM messages WHERE profileId = :profileId)")
    suspend fun deleteOrphanBodies(profileId: Long)

    @Query("DELETE FROM message_attachments WHERE profileId = :profileId AND messageId NOT IN (SELECT id FROM messages WHERE profileId = :profileId)")
    suspend fun deleteOrphanAttachments(profileId: Long)

    /**
     * Stores the newest page of the inbox and keeps older messages. Only stored messages within
     * the page's time range that aren't on it were deleted in Librus; with [complete] (the page
     * holds the whole inbox) that's every message not on it.
     */
    @Transaction
    suspend fun mergeInbox(profileId: Long, items: List<MessageEntity>, complete: Boolean) {
        upsertAll(items)
        val from = if (complete) Long.MIN_VALUE else items.minOfOrNull { it.sentAt } ?: return
        deleteMissing(profileId, from, items.map { it.id })
        deleteOrphanBodies(profileId)
        deleteOrphanAttachments(profileId)
    }

    @Query("SELECT * FROM messages WHERE profileId = :profileId ORDER BY sentAt DESC")
    fun observeAll(profileId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE profileId = :profileId AND sentAt >= :since")
    suspend fun sentSince(profileId: Long, since: Long): List<MessageEntity>

    /**
     * The inbox with what's known about the recipients. A message id is the same in every
     * inbox it reached, so one fetched anywhere tells the count for all of them.
     */
    @Query(
        """
        SELECT m.*,
            (SELECT MAX(b.receiversCount) FROM message_bodies b WHERE b.messageId = m.id) AS receiversCount,
            (SELECT group_concat(o.profileId) FROM messages o WHERE o.id = m.id AND o.profileId != m.profileId) AS otherProfileIds
        FROM messages m WHERE m.profileId = :profileId ORDER BY m.sentAt DESC
        """
    )
    fun observeInbox(profileId: Long): Flow<List<InboxMessage>>

    @Query(
        """
        SELECT m.*,
            (SELECT MAX(b.receiversCount) FROM message_bodies b WHERE b.messageId = m.id) AS receiversCount,
            (SELECT group_concat(o.profileId) FROM messages o WHERE o.id = m.id AND o.profileId != m.profileId) AS otherProfileIds
        FROM messages m WHERE m.profileId = :profileId AND m.id = :id
        """
    )
    fun observeInboxMessage(profileId: Long, id: Long): Flow<InboxMessage?>

    @Query("SELECT messageId FROM message_bodies WHERE profileId = :profileId AND receiversCount IS NOT NULL")
    suspend fun idsWithReceivers(profileId: Long): List<Long>

    @Query("SELECT * FROM messages WHERE profileId = :profileId AND readAt IS NULL ORDER BY sentAt DESC")
    fun observeUnread(profileId: Long): Flow<List<MessageEntity>>

    @Query("SELECT COUNT(*) FROM messages WHERE profileId = :profileId AND readAt IS NULL")
    fun observeUnreadCount(profileId: Long): Flow<Int>

    @Query("SELECT * FROM messages WHERE profileId = :profileId AND id = :id")
    fun observe(profileId: Long, id: Long): Flow<MessageEntity?>

    @Query("UPDATE messages SET readAt = :readAt WHERE profileId = :profileId AND id = :id AND readAt IS NULL")
    suspend fun markRead(profileId: Long, id: Long, readAt: Long)

    @Query("SELECT * FROM message_bodies WHERE profileId = :profileId AND messageId = :messageId")
    fun observeBody(profileId: Long, messageId: Long): Flow<MessageBodyEntity?>

    @Query("SELECT * FROM message_bodies WHERE profileId = :profileId")
    fun observeBodies(profileId: Long): Flow<List<MessageBodyEntity>>

    @Query("SELECT * FROM message_attachments WHERE profileId = :profileId AND messageId = :messageId ORDER BY id")
    fun observeAttachments(profileId: Long, messageId: Long): Flow<List<MessageAttachmentEntity>>

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsertBody(body: MessageBodyEntity)

    @Query("DELETE FROM message_attachments WHERE profileId = :profileId AND messageId = :messageId")
    suspend fun deleteAttachments(profileId: Long, messageId: Long)

    @Insert
    suspend fun insertAttachments(items: List<MessageAttachmentEntity>)

    @Transaction
    suspend fun saveDetails(body: MessageBodyEntity, attachments: List<MessageAttachmentEntity>, readAt: Long?) {
        upsertBody(body)
        deleteAttachments(body.profileId, body.messageId)
        insertAttachments(attachments)
        if (readAt != null) markRead(body.profileId, body.messageId, readAt)
    }
}

@Dao
interface LuckyNumberDao {
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun upsert(item: LuckyNumberEntity)

    @Query("SELECT * FROM lucky_numbers WHERE profileId = :profileId AND date >= :from ORDER BY date LIMIT 1")
    fun observeFrom(profileId: Long, from: LocalDate): Flow<LuckyNumberEntity?>
}

@Dao
interface TimetableDao {
    @Query("DELETE FROM timetable WHERE profileId = :profileId AND date BETWEEN :from AND :to")
    suspend fun deleteRange(profileId: Long, from: LocalDate, to: LocalDate)

    @Insert
    suspend fun insertAll(items: List<TimetableLessonEntity>)

    /** Replaces the given date range, so lessons removed by Librus disappear too. */
    @Transaction
    suspend fun replaceRange(profileId: Long, from: LocalDate, to: LocalDate, items: List<TimetableLessonEntity>) {
        deleteRange(profileId, from, to)
        insertAll(items)
    }

    @Query("SELECT * FROM timetable WHERE profileId = :profileId AND date BETWEEN :from AND :to ORDER BY date, lessonNo, position")
    fun observeRange(profileId: Long, from: LocalDate, to: LocalDate): Flow<List<TimetableLessonEntity>>

    @Query("SELECT * FROM timetable WHERE date BETWEEN :from AND :to ORDER BY date, lessonNo, position")
    fun observeRangeAllProfiles(from: LocalDate, to: LocalDate): Flow<List<TimetableLessonEntity>>
}

@Dao
interface FeedDao {
    /** Entries already logged are ignored thanks to the unique (childKey, kind, refKey) index. */
    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<FeedEntryEntity>)

    @Query("SELECT * FROM feed ORDER BY occurredAt DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int = 500): Flow<List<FeedEntryEntity>>

    /** Entries logged after [afterId]; ids only grow, so this is "what's new since then". */
    @Query("SELECT * FROM feed WHERE id > :afterId ORDER BY id")
    suspend fun after(afterId: Long): List<FeedEntryEntity>

    @Query("SELECT MAX(id) FROM feed")
    suspend fun maxId(): Long?

    @Query("DELETE FROM feed WHERE occurredAt < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("UPDATE OR IGNORE feed SET childKey = :to WHERE childKey = :from")
    suspend fun renameChild(from: String, to: String)

    @Query("DELETE FROM feed WHERE childKey = :childKey")
    suspend fun deleteChild(childKey: String)

    /** Entries that already exist under [to] win over their duplicates under [from]. */
    @Transaction
    suspend fun moveChild(from: String, to: String) {
        if (from == to) return
        renameChild(from, to)
        deleteChild(from)
    }

    /** Drops entries of [kind] dated within [from]..[to] that no longer describe the current state. */
    @Query("DELETE FROM feed WHERE childKey = :childKey AND kind = :kind AND date BETWEEN :from AND :to AND refKey NOT IN (:current)")
    suspend fun deleteOutdated(childKey: String, kind: FeedKind, from: LocalDate, to: LocalDate, current: List<String>)
}

@Dao
interface HiddenAnnouncementDao {
    @Query("SELECT id FROM hidden_announcements")
    fun observeIds(): Flow<List<String>>

    @Upsert
    suspend fun hide(row: HiddenAnnouncementEntity)

    @Query("DELETE FROM hidden_announcements WHERE id = :id")
    suspend fun show(id: String)
}

@Dao
interface CalendarLinkDao {
    @Query("SELECT * FROM calendar_links")
    suspend fun all(): List<CalendarLinkEntity>

    @Upsert
    suspend fun upsert(link: CalendarLinkEntity)

    @Query("DELETE FROM calendar_links WHERE `key` = :key")
    suspend fun delete(key: String)
}

@Dao
interface SectionSyncDao {
    @Query("SELECT * FROM section_syncs WHERE profileId = :profileId")
    suspend fun all(profileId: Long): List<SectionSyncEntity>

    @Upsert
    suspend fun upsert(row: SectionSyncEntity)
}

data class InboxMessage(
    @Embedded val message: MessageEntity,
    val receiversCount: Int?,
    /** Comma-separated ids of the user's other profiles that got the same message. */
    val otherProfileIds: String?,
) {
    val alsoIn: List<Long> get() = otherProfileIds?.split(',')?.mapNotNull { it.trim().toLongOrNull() }.orEmpty()
    val audience: MessageAudience get() = MessageAudience.of(receiversCount, alsoIn.isNotEmpty())
}
