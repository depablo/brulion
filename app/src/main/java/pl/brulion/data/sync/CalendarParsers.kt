/*
 * Part of Brulion, GNU GPL v3. Based on Librus support in Szkolny.eu
 * (https://github.com/szkolny-eu/szkolny-android), (c) Kuba Szczodrzyński and contributors, GPL v3.
 */
package pl.brulion.data.sync

import pl.brulion.data.db.PtMeetingEntity
import pl.brulion.data.db.TeacherAbsenceEntity
import pl.brulion.librus.long
import pl.brulion.librus.normalizeSpaces
import pl.brulion.librus.objects
import pl.brulion.librus.parseLibrusDate
import pl.brulion.librus.refId
import pl.brulion.librus.requiredArray
import pl.brulion.librus.string

/** Teacher absences from TeacherFreeDays; an item without an id or dates can't be shown and is skipped. */
internal class TeacherAbsenceParser(private val session: SyncSession, private val profileId: Long) {
    suspend fun parse(): List<TeacherAbsenceEntity> {
        val teachers = session.teachers()
        return session.get("TeacherFreeDays").requiredArray("TeacherFreeDays").objects().mapNotNull { item ->
            TeacherAbsenceEntity(
                profileId = profileId,
                id = item.long("Id") ?: return@mapNotNull null,
                dateFrom = parseLibrusDate(item.string("DateFrom")) ?: return@mapNotNull null,
                dateTo = parseLibrusDate(item.string("DateTo")) ?: return@mapNotNull null,
                timeFrom = item.string("TimeFrom").asClockTime(),
                timeTo = item.string("TimeTo").asClockTime(),
                teacherName = item.refId("Teacher")?.let(teachers::get),
                firstSeenAt = 0,
            )
        }
    }
}

/** Parent-teacher meetings from ParentTeacherConferences. */
internal class PtMeetingParser(private val session: SyncSession, private val profileId: Long) {
    suspend fun parse(): List<PtMeetingEntity> {
        val teachers = session.teachers()
        return session.get("ParentTeacherConferences").requiredArray("ParentTeacherConferences").objects().mapNotNull { item ->
            PtMeetingEntity(
                profileId = profileId,
                id = item.long("Id") ?: return@mapNotNull null,
                date = parseLibrusDate(item.string("Date")) ?: return@mapNotNull null,
                startTime = item.string("Time").asClockTime(),
                teacherName = item.refId("Teacher")?.let(teachers::get),
                topic = item.string("Topic").orEmpty().normalizeSpaces(),
                firstSeenAt = 0,
            )
        }
    }
}

/** "08:30:00" -> "08:30"; Librus sends midnight when there is no time. */
private fun String?.asClockTime(): String? =
    this?.takeIf { it.isNotBlank() && it != "00:00:00" }?.take(5)
