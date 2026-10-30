package pl.brulion.data.calendar

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import pl.brulion.data.AccountRepository
import pl.brulion.data.Diagnostics
import pl.brulion.data.Settings
import pl.brulion.data.db.BrulionDatabase
import pl.brulion.data.db.CalendarLinkEntity
import pl.brulion.data.school.TIMETABLE_WEEKS
import pl.brulion.librus.schoolToday
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Keeps the planner in a phone calendar the user picked: adds new entries, updates changed
 * ones and removes those Librus dropped. Past entries are left alone.
 */
class CalendarExport(
    private val context: Context,
    private val db: BrulionDatabase,
    private val accounts: AccountRepository,
    private val settings: Settings,
    private val diagnostics: Diagnostics,
) {
    data class Calendar(val id: Long, val name: String, val account: String)

    private val lock = Mutex()

    fun hasPermission(): Boolean = PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /** Calendars the user can write to. */
    fun calendars(): List<Calendar> {
        if (!hasPermission()) return emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
        )
        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}"
        return context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, selection, null, null)?.use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(Calendar(cursor.getLong(0), cursor.getString(1).orEmpty(), cursor.getString(2).orEmpty()))
            }
        }.orEmpty()
    }

    suspend fun sync() = lock.withLock {
        val calendarId = settings.exportCalendarId.value ?: return@withLock
        if (!hasPermission()) return@withLock
        withContext(Dispatchers.IO) {
            try {
                write(calendarId)
            } catch (e: Exception) {
                diagnostics.log("calendar export failed", e)
            }
        }
    }

    /** Exports into [calendarId] from now on; entries already in another calendar move over. */
    suspend fun use(calendarId: Long) {
        if (settings.exportCalendarId.value != calendarId) disable()
        settings.setExportCalendarId(calendarId)
        sync()
    }

    /** Turns the export off and takes the upcoming entries back out of the calendar. */
    suspend fun disable() = lock.withLock {
        settings.setExportCalendarId(null)
        withContext(Dispatchers.IO) {
            val today = schoolToday()
            for (link in db.calendarLinkDao().all()) {
                if (link.date >= today && hasPermission()) delete(link.calendarEventId)
                db.calendarLinkDao().delete(link.key)
            }
        }
    }

    private suspend fun write(calendarId: Long) {
        val today = schoolToday()
        val profiles = accounts.allProfiles.first()
        val events = profiles.flatMap { db.eventDao().observeAll(it.id).first() }
        val lessons = db.timetableDao().observeRangeAllProfiles(today, today.plusWeeks(TIMETABLE_WEEKS)).first()
        val entries = calendarEntries(profiles, events, lessons, today).associateBy { it.key }
        val links = db.calendarLinkDao().all().associateBy { it.key }

        for (entry in entries.values) {
            val link = links[entry.key]
            when {
                link == null -> insert(calendarId, entry)?.let { save(entry, it) }
                link.fingerprint != entry.fingerprint -> {
                    // Gone from the calendar (deleted by the user or with the calendar): add it again.
                    if (update(link.calendarEventId, entry)) save(entry, link.calendarEventId)
                    else insert(calendarId, entry)?.let { save(entry, it) }
                }
            }
        }
        // Entries Librus no longer has, e.g. a cancelled test; past ones stay as a record.
        for (link in links.values) {
            if (link.key !in entries && link.date >= today) {
                delete(link.calendarEventId)
                db.calendarLinkDao().delete(link.key)
            }
        }
    }

    private suspend fun save(entry: CalendarEntry, eventId: Long) =
        db.calendarLinkDao().upsert(CalendarLinkEntity(entry.key, eventId, entry.date, entry.fingerprint))

    private fun values(entry: CalendarEntry) = ContentValues().apply {
        put(CalendarContract.Events.TITLE, entry.title)
        put(CalendarContract.Events.DESCRIPTION, entry.description)
        put(CalendarContract.Events.DTSTART, entry.start)
        put(CalendarContract.Events.DTEND, entry.end)
        put(CalendarContract.Events.ALL_DAY, if (entry.allDay) 1 else 0)
        put(CalendarContract.Events.EVENT_TIMEZONE, if (entry.allDay) ZoneOffset.UTC.id else ZoneId.systemDefault().id)
    }

    private fun insert(calendarId: Long, entry: CalendarEntry): Long? {
        val uri = context.contentResolver.insert(
            CalendarContract.Events.CONTENT_URI,
            values(entry).apply { put(CalendarContract.Events.CALENDAR_ID, calendarId) },
        )
        return uri?.let(ContentUris::parseId)
    }

    private fun update(eventId: Long, entry: CalendarEntry): Boolean =
        context.contentResolver.update(eventUri(eventId), values(entry), null, null) > 0

    private fun delete(eventId: Long) {
        context.contentResolver.delete(eventUri(eventId), null, null)
    }

    private fun eventUri(id: Long) = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)

    companion object {
        val PERMISSIONS = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
    }
}
