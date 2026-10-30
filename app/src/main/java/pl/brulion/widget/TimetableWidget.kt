package pl.brulion.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pl.brulion.BrulionApp
import pl.brulion.R
import pl.brulion.data.db.LessonState
import pl.brulion.data.school.TIMETABLE_WEEKS
import pl.brulion.data.school.eventsByLesson
import pl.brulion.data.school.nextLessonDay
import pl.brulion.librus.schoolToday
import pl.brulion.ui.MainActivity
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Home and lock screen widget with the lessons for today or the next school day. Each widget
 * shows the student chosen in [TimetableWidgetConfigActivity], or the one selected in the app.
 */
class TimetableWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        scope.launch {
            try {
                appWidgetIds.forEach { manager.updateAppWidget(it, render(context, it)) }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { setProfile(context, it, null) }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private const val MAX_ROWS = 9
        private val DayFormat = DateTimeFormatter.ofPattern("EEEE, d.MM", Locale.forLanguageTag("pl-PL"))

        /** Refreshes every placed widget, e.g. after a sync or switching the student. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, TimetableWidget::class.java))
            if (ids.isEmpty()) return
            scope.launch { ids.forEach { manager.updateAppWidget(it, render(context, it)) } }
        }

        fun update(context: Context, appWidgetId: Int) {
            scope.launch { AppWidgetManager.getInstance(context).updateAppWidget(appWidgetId, render(context, appWidgetId)) }
        }

        private fun prefs(context: Context) = context.getSharedPreferences("widgets", Context.MODE_PRIVATE)

        /** The student shown by a widget; null follows the student selected in the app. */
        fun profileOf(context: Context, appWidgetId: Int): Long? =
            prefs(context).getLong("profile_$appWidgetId", 0).takeIf { it != 0L }

        fun setProfile(context: Context, appWidgetId: Int, profileId: Long?) {
            prefs(context).edit().apply {
                if (profileId == null) remove("profile_$appWidgetId") else putLong("profile_$appWidgetId", profileId)
            }.apply()
        }

        private suspend fun render(context: Context, appWidgetId: Int): RemoteViews {
            val container = (context.applicationContext as BrulionApp).container
            val views = RemoteViews(context.packageName, R.layout.widget_timetable)
            views.removeAllViews(R.id.widget_rows)

            // A student removed from the app falls back to the selected one.
            val chosen = profileOf(context, appWidgetId)
                ?.let { id -> container.accounts.allProfiles.first().find { it.id == id } }
            // Tapping opens the student the widget shows; one request code per widget, so
            // widgets of different students don't share (and overwrite) one intent.
            val open = Intent(context, MainActivity::class.java)
                .apply { chosen?.let { putExtra(MainActivity.EXTRA_PROFILE_ID, it.id) } }
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(context, appWidgetId, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
            val profile = chosen ?: container.currentProfile.resolve()
            if (profile == null) {
                views.setTextViewText(R.id.widget_title, context.getString(R.string.app_name))
                return views.showEmpty(context.getString(R.string.widget_no_profile))
            }
            val today = schoolToday()
            val lessons = container.database.timetableDao()
                .observeRange(profile.id, today, today.plusWeeks(TIMETABLE_WEEKS)).first()
            val (day, dayLessons) = nextLessonDay(lessons, today)
                ?: return views.apply { setTextViewText(R.id.widget_title, context.getString(R.string.widget_timetable_name)) }
                    .showEmpty(context.getString(R.string.timetable_empty))

            val dayLabel = when (day) {
                today -> context.getString(R.string.tab_today)
                today.plusDays(1) -> context.getString(R.string.widget_tomorrow)
                else -> day.format(DayFormat).replaceFirstChar { it.uppercase() }
            }
            // No name: the widget may sit on the lock screen, visible to anyone.
            views.setTextViewText(R.id.widget_title, dayLabel)
            views.setViewVisibility(R.id.widget_empty, View.GONE)
            val events = container.database.eventDao().observeAll(profile.id).first().filter { it.date == day }
            val byLesson = eventsByLesson(dayLessons, events)
            dayLessons.take(MAX_ROWS).forEach { lesson ->
                val row = RemoteViews(context.packageName, R.layout.widget_timetable_row)
                row.setTextViewText(R.id.row_number, lesson.lessonNo.toString())
                row.setTextViewText(R.id.row_time, lesson.startTime.orEmpty())
                val subject = lesson.subjectName ?: lesson.originalSubjectName ?: "—"
                val note = when (lesson.state) {
                    LessonState.NORMAL -> null
                    LessonState.CANCELLED -> context.getString(R.string.lesson_cancelled)
                    LessonState.SUBSTITUTION -> context.getString(R.string.lesson_substitution)
                    LessonState.MOVED_AWAY, LessonState.MOVED_HERE -> context.getString(R.string.lesson_moved)
                }
                val room = lesson.classroom?.let { " · " + context.getString(R.string.lesson_classroom, it) }.orEmpty()
                // A test on that lesson is mentioned the same quiet way as a substitution.
                val notes = listOfNotNull(note) + byLesson[lesson].orEmpty().map { it.typeName }
                val suffix = if (notes.isEmpty()) "" else notes.joinToString(", ", " (", ")").lowercase()
                row.setTextViewText(R.id.row_subject, subject + room + suffix)
                if (note != null) {
                    row.setTextColor(R.id.row_subject, context.getColor(R.color.widget_warning))
                }
                views.addView(R.id.widget_rows, row)
            }
            return views
        }

        private fun RemoteViews.showEmpty(text: String): RemoteViews = apply {
            setTextViewText(R.id.widget_empty, text)
            setViewVisibility(R.id.widget_empty, View.VISIBLE)
        }
    }
}
