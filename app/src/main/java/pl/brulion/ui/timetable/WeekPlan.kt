package pl.brulion.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.brulion.R
import pl.brulion.data.db.LessonState
import pl.brulion.data.db.TimetableLessonEntity
import pl.brulion.data.school.timetableWeekStart
import pl.brulion.ui.common.EmptyState
import pl.brulion.ui.common.appContainer
import pl.brulion.ui.common.formatShort
import pl.brulion.ui.common.formatWeekday
import pl.brulion.ui.common.rememberToday
import java.time.DayOfWeek
import java.time.LocalDate

/** Lessons of one week laid out as a grid: a row per lesson number, a column per day. */
internal class WeekGrid(
    val lessonNumbers: List<Int>,
    private val startTimes: Map<Int, String?>,
    private val cells: Map<Pair<LocalDate, Int>, List<TimetableLessonEntity>>,
) {
    fun startTime(lessonNo: Int): String? = startTimes[lessonNo]

    fun at(day: LocalDate, lessonNo: Int): List<TimetableLessonEntity> = cells[day to lessonNo].orEmpty()
}

internal fun weekGrid(lessons: List<TimetableLessonEntity>): WeekGrid {
    val numbers = lessons.map { it.lessonNo }.distinct().sorted()
    val starts = numbers.associateWith { no -> lessons.filter { it.lessonNo == no }.firstNotNullOfOrNull { it.startTime } }
    val cells = lessons.sortedBy { it.position }.groupBy { it.date to it.lessonNo }
    return WeekGrid(numbers, starts, cells)
}

/** The days shown: Monday to Friday, and the weekend only when something is planned then. */
internal fun weekDays(monday: LocalDate, lessons: List<TimetableLessonEntity>): List<LocalDate> =
    (0 until 7).map { monday.plusDays(it.toLong()) }
        .filter { it.dayOfWeek < DayOfWeek.SATURDAY || lessons.any { lesson -> lesson.date == it } }

@Composable
fun WeekPlan(profileId: Long, modifier: Modifier = Modifier) {
    val db = appContainer().database
    val today = rememberToday()
    var monday by remember { mutableStateOf(timetableWeekStart(today)) }
    val sunday = monday.plusDays(6)
    val lessons by remember(profileId, monday) { db.timetableDao().observeRange(profileId, monday, sunday) }
        .collectAsStateWithLifecycle(null)
    val all = lessons ?: return
    if (all.isEmpty()) {
        Column(modifier) {
            WeekSwitcher(monday, sunday, onPrevious = { monday = monday.minusWeeks(1) }, onNext = { monday = monday.plusWeeks(1) })
            EmptyState(Icons.Outlined.EventBusy, stringResource(R.string.timetable_empty))
        }
        return
    }
    val grid = remember(all) { weekGrid(all) }
    val days = remember(all, monday) { weekDays(monday, all) }

    Column(modifier.padding(horizontal = 8.dp)) {
        WeekSwitcher(monday, sunday, onPrevious = { monday = monday.minusWeeks(1) }, onNext = { monday = monday.plusWeeks(1) })
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(52.dp))
            days.forEach { day ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(day.formatWeekday(), style = MaterialTheme.typography.labelMedium)
                    Text(
                        text = day.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (day == today) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(grid.lessonNumbers, key = { it }) { lessonNo ->
                Row(Modifier.fillMaxWidth().height(64.dp)) {
                    Box(Modifier.width(52.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                        Text(
                            text = grid.startTime(lessonNo) ?: lessonNo.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    days.forEach { day -> WeekCell(grid.at(day, lessonNo), Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun WeekSwitcher(monday: LocalDate, sunday: LocalDate, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.week_previous))
        }
        Text(
            text = "${monday.formatShort()} – ${sunday.formatShort()}",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.week_next))
        }
    }
}

@Composable
private fun WeekCell(lessons: List<TimetableLessonEntity>, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val background = when {
        lessons.isEmpty() -> Color.Transparent
        lessons.all { it.state == LessonState.CANCELLED } -> colors.errorContainer
        lessons.any { it.state == LessonState.SUBSTITUTION || it.state == LessonState.MOVED_HERE } -> colors.tertiaryContainer
        else -> colors.secondaryContainer
    }
    Box(modifier.padding(2.dp).fillMaxHeight().background(background, RoundedCornerShape(6.dp)).padding(4.dp)) {
        if (lessons.isNotEmpty()) {
            Column {
                lessons.forEach { lesson ->
                    val crossedOut = lesson.state == LessonState.CANCELLED || lesson.state == LessonState.MOVED_AWAY
                    Text(
                        text = lesson.subjectName ?: "–",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = if (crossedOut) colors.onSurfaceVariant else colors.onSurface,
                        textDecoration = if (crossedOut) TextDecoration.LineThrough else null,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    lesson.classroom?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1)
                    }
                }
            }
        }
    }
}
