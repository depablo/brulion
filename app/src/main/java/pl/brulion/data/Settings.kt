package pl.brulion.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import pl.brulion.data.db.FeedKind

/** Small app preferences; tokens and school data live in the database. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("brulion", Context.MODE_PRIVATE)

    private val _summaryEnabled = MutableStateFlow(prefs.getBoolean(KEY_SUMMARY_ENABLED, true))
    val summaryEnabled: StateFlow<Boolean> = _summaryEnabled

    /** Minutes after midnight. */
    private val _summaryTime = MutableStateFlow(prefs.getInt(KEY_SUMMARY_TIME, 19 * 60))
    val summaryTime: StateFlow<Int> = _summaryTime

    private val _updateAutoCheck = MutableStateFlow(prefs.getBoolean(KEY_UPDATE_AUTO_CHECK, true))
    val updateAutoCheck: StateFlow<Boolean> = _updateAutoCheck

    /** Whether a summary with nothing new is still sent. */
    private val _summaryWhenQuiet = MutableStateFlow(prefs.getBoolean(KEY_SUMMARY_WHEN_QUIET, false))
    val summaryWhenQuiet: StateFlow<Boolean> = _summaryWhenQuiet

    fun setSummaryWhenQuiet(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SUMMARY_WHEN_QUIET, enabled).apply()
        _summaryWhenQuiet.value = enabled
    }

    /** Announcements mentioning any of these words are hidden, e.g. "matura" in a school with a high school. */
    private val _mutedWords = MutableStateFlow(parseWords(prefs.getString(KEY_MUTED_WORDS, null).orEmpty()))
    val mutedWords: StateFlow<List<String>> = _mutedWords

    fun setMutedWords(text: String) {
        val words = parseWords(text)
        prefs.edit().putString(KEY_MUTED_WORDS, words.joinToString(", ")).apply()
        _mutedWords.value = words
    }

    /** The phone calendar that gets the planner, or null when the export is off. */
    private val _exportCalendarId = MutableStateFlow(prefs.getLong(KEY_EXPORT_CALENDAR, -1).takeIf { it >= 0 })
    val exportCalendarId: StateFlow<Long?> = _exportCalendarId

    fun setExportCalendarId(id: Long?) {
        prefs.edit().apply { if (id == null) remove(KEY_EXPORT_CALENDAR) else putLong(KEY_EXPORT_CALENDAR, id) }.apply()
        _exportCalendarId.value = id
    }

    /** Kinds of feed entries notified as they arrive; empty when the instant notifications are off. */
    private val _instantKinds = MutableStateFlow(
        prefs.getStringSet(KEY_INSTANT_KINDS, emptySet()).orEmpty().mapNotNull { name -> FeedKind.entries.find { it.name == name } }.toSet()
    )
    val instantKinds: StateFlow<Set<FeedKind>> = _instantKinds

    fun setInstantKinds(kinds: Set<FeedKind>) {
        prefs.edit().putStringSet(KEY_INSTANT_KINDS, kinds.map { it.name }.toSet()).apply()
        _instantKinds.value = kinds
    }

    /** The last feed entry the instant notifications looked at; -1 before the first check. */
    var instantSeenFeedId: Long
        get() = prefs.getLong(KEY_INSTANT_SEEN, -1)
        set(value) = prefs.edit().putLong(KEY_INSTANT_SEEN, value).apply()

    /** Which one-time rebuild of the feed has run; see AppContainer. */
    var feedRevision: Int
        get() = prefs.getInt(KEY_FEED_REVISION, 0)
        set(value) = prefs.edit().putInt(KEY_FEED_REVISION, value).apply()

    fun setUpdateAutoCheck(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_UPDATE_AUTO_CHECK, enabled).apply()
        _updateAutoCheck.value = enabled
    }

    fun setSummaryEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SUMMARY_ENABLED, enabled).apply()
        _summaryEnabled.value = enabled
    }

    fun setSummaryTime(minutes: Int) {
        prefs.edit().putInt(KEY_SUMMARY_TIME, minutes).apply()
        _summaryTime.value = minutes
    }

    /** When the last scheduled summary for a child was sent (epoch seconds), 0 if never. */
    fun lastSummaryAt(childKey: String): Long = prefs.getLong("$KEY_LAST_SUMMARY$childKey", 0)

    fun setLastSummaryAt(childKey: String, seconds: Long) {
        prefs.edit().putLong("$KEY_LAST_SUMMARY$childKey", seconds).apply()
    }

    fun moveLastSummaryAt(from: String, to: String) {
        val old = "$KEY_LAST_SUMMARY$from"
        if (!prefs.contains(old)) return
        val at = maxOf(prefs.getLong(old, 0), lastSummaryAt(to))
        prefs.edit().putLong("$KEY_LAST_SUMMARY$to", at).remove(old).apply()
    }

    private companion object {
        const val KEY_SUMMARY_ENABLED = "summary_enabled"
        const val KEY_SUMMARY_TIME = "summary_time"
        const val KEY_LAST_SUMMARY = "last_summary_"
        const val KEY_UPDATE_AUTO_CHECK = "update_auto_check"
        const val KEY_MUTED_WORDS = "muted_announcement_words"
        const val KEY_EXPORT_CALENDAR = "export_calendar_id"
        const val KEY_INSTANT_KINDS = "instant_kinds"
        const val KEY_FEED_REVISION = "feed_revision"
        const val KEY_SUMMARY_WHEN_QUIET = "summary_when_quiet"
        const val KEY_INSTANT_SEEN = "instant_seen_feed_id"

        /** "matura, LO;  olimpiada" -> [matura, LO, olimpiada] */
        fun parseWords(text: String): List<String> =
            text.split(',', ';', '\n').map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }
    }
}
