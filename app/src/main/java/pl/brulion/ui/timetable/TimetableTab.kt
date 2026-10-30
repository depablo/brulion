package pl.brulion.ui.timetable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pl.brulion.R
import pl.brulion.data.db.EventEntity
import pl.brulion.data.db.TimetableLessonEntity
import pl.brulion.data.school.TIMETABLE_WEEKS
import pl.brulion.data.school.eventsByLesson
import pl.brulion.data.school.timetableWeekStart
import pl.brulion.librus.schoolTimeNow
import pl.brulion.ui.common.EmptyState
import pl.brulion.ui.common.appContainer
import pl.brulion.ui.common.formatLong
import pl.brulion.ui.common.formatWeekday
import pl.brulion.ui.common.rememberToday
import pl.brulion.ui.events.EventRow
import java.time.DayOfWeek
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableTab(profileId: Long) {
    val db = appContainer().database
    val today = rememberToday()
    val monday = remember(today) { timetableWeekStart(today) }
    val lastDay = monday.plusWeeks(TIMETABLE_WEEKS).minusDays(1)
    val lessons by remember(profileId, monday) { db.timetableDao().observeRange(profileId, monday, lastDay) }
        .collectAsStateWithLifecycle(null)
    val events by remember(profileId) { db.eventDao().observeAll(profileId) }.collectAsStateWithLifecycle(emptyList())

    val byDate = lessons?.groupBy { it.date } ?: return
    // Weekends only when something is actually planned then.
    val days = (0 until TIMETABLE_WEEKS * 7).map { monday.plusDays(it) }
        .filter { it.dayOfWeek < DayOfWeek.SATURDAY || it in byDate }
    val pager = rememberPagerState(days.indexOfFirst { it >= today }.coerceAtLeast(0)) { days.size }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        PrimaryScrollableTabRow(selectedTabIndex = pager.currentPage, edgePadding = 8.dp) {
            days.forEachIndexed { index, day ->
                Tab(
                    selected = pager.currentPage == index,
                    onClick = { scope.launch { pager.animateScrollToPage(index) } },
                    text = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(day.formatWeekday(), style = MaterialTheme.typography.labelMedium)
                            Text(
                                text = day.dayOfMonth.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = if (day == today) FontWeight.Bold else FontWeight.Normal,
                            )
                        }
                    },
                )
            }
        }
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            val day = days[page]
            DayTimetable(day, byDate[day].orEmpty(), events.filter { it.date == day }, isToday = day == today)
        }
    }
}

@Composable
private fun DayTimetable(day: LocalDate, lessons: List<TimetableLessonEntity>, events: List<EventEntity>, isToday: Boolean) {
    if (lessons.isEmpty() && events.isEmpty()) {
        EmptyState(Icons.Outlined.EventBusy, stringResource(R.string.timetable_empty))
        return
    }
    val now = remember { schoolTimeNow() }
    val byLesson = remember(lessons, events) { eventsByLesson(lessons, events) }
    // Events that belong to no lesson (no such subject that day) stay listed above the plan.
    val loose = remember(byLesson, events) { events - byLesson.values.flatten().toSet() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(day.formatLong(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        if (loose.isNotEmpty()) {
            items(loose, key = { "e${it.id}" }) { EventRow(it) }
            item { HorizontalDivider() }
        }
        items(lessons, key = { "l${it.lessonNo}-${it.position}" }) { lesson ->
            val current = isToday && lesson.startTime != null && lesson.endTime != null &&
                now >= lesson.startTime && now < lesson.endTime
            LessonRow(lesson, Modifier.fillMaxWidth(), isCurrent = current, events = byLesson[lesson].orEmpty())
        }
    }
}
