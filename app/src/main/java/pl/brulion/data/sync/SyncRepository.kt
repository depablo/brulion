package pl.brulion.data.sync

import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import pl.brulion.data.AccountRepository
import pl.brulion.data.MessageSessions
import pl.brulion.data.db.BrulionDatabase
import pl.brulion.data.db.GradeEntity
import pl.brulion.data.db.GradeKind
import pl.brulion.data.db.LessonState
import pl.brulion.data.db.MessageEntity
import pl.brulion.data.db.SectionSyncEntity
import pl.brulion.data.db.TimetableLessonEntity
import pl.brulion.data.feed.FeedRecorder
import pl.brulion.data.school.TIMETABLE_WEEKS
import pl.brulion.data.school.timetableWeekStart
import pl.brulion.librus.LibrusApi
import pl.brulion.librus.LibrusException
import pl.brulion.librus.LibrusException.Reason
import pl.brulion.librus.nowSeconds

enum class SyncSection { GRADES, ANNOUNCEMENTS, ATTENDANCE, EVENTS, NOTES, LUCKY_NUMBER, TIMETABLE, MESSAGES }

sealed interface SectionResult {
    /** [note] is something odd but not fatal, worth a line in the diagnostics. */
    data class Ok(val total: Int, val new: Int, val note: String? = null) : SectionResult
    /** The school doesn't use this module or hides it (e.g. lucky number, public timetable). */
    data class Skipped(val reason: Reason) : SectionResult
    data class Failed(val error: Throwable) : SectionResult
}

class SyncReport(val results: Map<SyncSection, SectionResult>)

class SyncRepository(
    private val db: BrulionDatabase,
    private val accounts: AccountRepository,
    private val api: LibrusApi,
    private val messageSessions: MessageSessions,
    private val feed: FeedRecorder,
) {
    /** Runs on [Dispatchers.Default]: parsing whole-year JSON would stall the UI thread. */
    suspend fun sync(profileId: Long): SyncReport = withContext(Dispatchers.Default) { syncProfile(profileId) }

    private suspend fun syncProfile(profileId: Long): SyncReport = supervisorScope {
        val profile = db.profileDao().get(profileId)
            ?: throw LibrusException(Reason.NOT_FOUND, "profile $profileId")
        val lastOk = db.sectionSyncDao().all(profileId).associate { it.section to it.lastOkAt }
        val session = SyncSession(this, profileId, accounts, api)

        val jobs = SyncSection.entries.associateWith { section ->
            // Per section, so one that failed on the very first sync doesn't later report the
            // whole school year as new.
            val sectionLastOk = lastOk[section.name] ?: 0L
            val newStamp = if (sectionLastOk == 0L) 0L else nowSeconds()
            async {
                runSection { syncSection(section, session, profileId, newStamp, sectionLastOk) }.also {
                    if (it is SectionResult.Ok) db.sectionSyncDao().upsert(SectionSyncEntity(profileId, section.name, nowSeconds()))
                }
            }
        }
        val results = jobs.mapValues { it.value.await() }

        if (results.values.any { it is SectionResult.Ok }) {
            db.profileDao().setLastSyncAt(profileId, nowSeconds())
            feed.record(profile)
        }
        SyncReport(results)
    }

    private suspend fun syncSection(
        section: SyncSection,
        session: SyncSession,
        profileId: Long,
        newStamp: Long,
        sectionLastOkAt: Long,
    ): SectionResult = when (section) {
        SyncSection.GRADES -> syncGrades(session, profileId, newStamp)

        SyncSection.ANNOUNCEMENTS -> {
            val items = AnnouncementParser(session, profileId).parse()
            val dao = db.announcementDao()
            val stamper = Stamper(dao.seen(profileId), newStamp)
            dao.replaceAll(profileId, items.map { it.copy(firstSeenAt = stamper.stamp(it.id)) })
            SectionResult.Ok(items.size, stamper.newCount)
        }

        SyncSection.ATTENDANCE -> {
            val items = AttendanceParser(session, profileId).parse()
            val dao = db.attendanceDao()
            val stamper = Stamper(dao.seen(profileId), newStamp)
            dao.replaceAll(profileId, items.map { it.copy(firstSeenAt = stamper.stamp(it.id)) })
            SectionResult.Ok(items.size, stamper.newCount)
        }

        SyncSection.EVENTS -> {
            val items = EventParser(session, profileId).parse()
            val dao = db.eventDao()
            val stamper = Stamper(dao.seen(profileId), newStamp)
            dao.replaceAll(profileId, items.map { it.copy(firstSeenAt = stamper.stamp(it.id.toString())) })
            SectionResult.Ok(items.size, stamper.newCount)
        }

        SyncSection.NOTES -> {
            val items = NoteParser(session, profileId).parse()
            val dao = db.noteDao()
            val stamper = Stamper(dao.seen(profileId), newStamp)
            dao.replaceAll(profileId, items.map { it.copy(firstSeenAt = stamper.stamp(it.id.toString())) })
            SectionResult.Ok(items.size, stamper.newCount)
        }

        SyncSection.LUCKY_NUMBER -> {
            val lucky = LuckyNumberParser(session, profileId).parse()
            lucky?.let { db.luckyNumberDao().upsert(it) }
            SectionResult.Ok(if (lucky != null) 1 else 0, 0)
        }

        SyncSection.TIMETABLE -> {
            val monday = timetableWeekStart()
            val parser = TimetableParser(session, profileId)
            val weeks = (0 until TIMETABLE_WEEKS).map { monday.plusWeeks(it) }.map { it to parser.parseWeek(it) }
            val lastDay = monday.plusWeeks(TIMETABLE_WEEKS).minusDays(1)
            // "New" for the timetable means changes (cancellations, substitutions...) not known before.
            fun changeKeys(lessons: List<TimetableLessonEntity>) =
                lessons.filter { it.state != LessonState.NORMAL }.map { listOf(it.date, it.lessonNo, it.position, it.state) }.toSet()
            val knownChanges = changeKeys(db.timetableDao().observeRange(profileId, monday, lastDay).first())
            db.withTransaction {
                for ((start, lessons) in weeks) {
                    db.timetableDao().replaceRange(profileId, start, start.plusDays(6), lessons)
                }
            }
            SectionResult.Ok(
                total = weeks.sumOf { it.second.size },
                new = (changeKeys(weeks.flatMap { it.second }) - knownChanges).size,
                note = parser.skipped.takeIf { it > 0 }?.let { "$it lesson(s) without LessonNo skipped" },
            )
        }

        SyncSection.MESSAGES -> {
            val page = messageSessions.withSession(profileId) { it.inbox(INBOX_PAGE) }
            val items = page.mapNotNull { parseMessage(profileId, it) }
            val dao = db.messageDao()
            val stamper = Stamper(dao.seen(profileId), newStamp)
            // Messages sent before the previous sync of this section were missed, not received now,
            // so they shouldn't show up in the summary as new.
            dao.mergeInbox(profileId, items.map {
                it.copy(firstSeenAt = stamper.stamp(it.id.toString(), backfill = it.sentAt <= sectionLastOkAt))
            }, complete = page.size < INBOX_PAGE)
            fetchReadDetails(profileId, items)
            SectionResult.Ok(items.size, stamper.newCount)
        }
    }

    /**
     * Details (recipient count, body) of messages already read: fetching them is what marks a
     * message as read in Librus, so unread ones are left for the user to open. Best effort,
     * a few per sync, newest first.
     */
    private suspend fun fetchReadDetails(profileId: Long, items: List<MessageEntity>) {
        val dao = db.messageDao()
        val known = dao.idsWithReceivers(profileId).toSet()
        val missing = items.filter { it.readAt != null && it.id !in known }.sortedByDescending { it.sentAt }
        for (message in missing.take(DETAILS_PER_SYNC)) {
            val json = try {
                messageSessions.withSession(profileId) { it.message(message.id) }
            } catch (e: LibrusException) {
                return
            }
            val details = parseMessageDetails(profileId, message.id, json, nowSeconds())
            dao.saveDetails(details.body, details.attachments, readAt = null)
        }
    }

    /** Normal grades must succeed; point and descriptive grades are optional modules. */
    private suspend fun syncGrades(session: SyncSession, profileId: Long, newStamp: Long): SectionResult {
        val parser = GradeParser(session, profileId)
        val normal = parser.normal()
        val optional = mapOf(
            GradeKind.POINT to optionalModule { parser.point() },
            GradeKind.DESCRIPTIVE to optionalModule { parser.descriptive() },
        )
        val dao = db.gradeDao()
        val stamper = Stamper(dao.seen(profileId), newStamp)
        fun List<GradeEntity>.stamped() = map { it.copy(firstSeenAt = stamper.stamp("${it.kind}:${it.id}")) }

        var total = normal.size
        db.withTransaction {
            dao.replaceKind(profileId, GradeKind.NORMAL, normal.stamped())
            // A module that failed keeps its old rows, so they don't come back as "new" next time.
            for ((kind, grades) in optional) {
                if (grades != null) {
                    dao.replaceKind(profileId, kind, grades.stamped())
                    total += grades.size
                }
            }
        }
        return SectionResult.Ok(total, stamper.newCount)
    }

    private suspend fun <T> optionalModule(block: suspend () -> List<T>): List<T>? = try {
        block()
    } catch (e: LibrusException) {
        if (e.reason in SOFT_REASONS) emptyList() else null
    }

    private suspend fun runSection(block: suspend () -> SectionResult): SectionResult = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: LibrusException) {
        if (e.reason in SOFT_REASONS) SectionResult.Skipped(e.reason) else SectionResult.Failed(e)
    } catch (e: Exception) {
        SectionResult.Failed(e)
    }

    private companion object {
        val SOFT_REASONS = setOf(Reason.FEATURE_DISABLED, Reason.ACCESS_DENIED, Reason.NOT_FOUND)
        const val INBOX_PAGE = 50
        const val DETAILS_PER_SYNC = 15
    }
}
