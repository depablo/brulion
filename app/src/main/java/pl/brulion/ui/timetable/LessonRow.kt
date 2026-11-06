package pl.brulion.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pl.brulion.R
import pl.brulion.data.db.EventEntity
import pl.brulion.data.db.LessonState
import pl.brulion.data.db.TimetableLessonEntity
import pl.brulion.ui.common.formatLong
import pl.brulion.ui.common.formatShort

@Composable
fun LessonRow(
    lesson: TimetableLessonEntity,
    modifier: Modifier = Modifier,
    isCurrent: Boolean = false,
    /** Tests and other entries from the planner that take place in this lesson. */
    events: List<EventEntity> = emptyList(),
) {
    val crossedOut = lesson.state == LessonState.CANCELLED || lesson.state == LessonState.MOVED_AWAY
    val colors = MaterialTheme.colorScheme
    var showDetails by rememberSaveable(lesson.date, lesson.lessonNo, lesson.position) { mutableStateOf(false) }
    Row(modifier.clickable { showDetails = true }, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(if (isCurrent) colors.primary else colors.secondaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = lesson.lessonNo.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (isCurrent) colors.onPrimary else colors.onSecondaryContainer,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = lesson.subjectName ?: lesson.originalSubjectName ?: "—",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                textDecoration = if (crossedOut) TextDecoration.LineThrough else null,
                color = if (crossedOut) colors.onSurfaceVariant else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val details = listOfNotNull(
                lesson.startTime?.let { start -> lesson.endTime?.let { "$start–$it" } ?: start },
                lesson.classroom?.let { stringResource(R.string.lesson_classroom, it) },
                lesson.teacherName,
            ).joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            stateLabel(lesson)?.let { (text, color) ->
                Text(text, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
            }
            events.forEach { event ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(Color(event.color), CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = eventLabel(event),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
    if (showDetails) LessonDetailDialog(lesson, events) { showDetails = false }
}

/** Everything about one lesson and the planner entries attached to it, without any cut-off lines. */
@Composable
private fun LessonDetailDialog(lesson: TimetableLessonEntity, events: List<EventEntity>, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(lesson.subjectName ?: lesson.originalSubjectName ?: "—") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.lesson_number, lesson.lessonNo) + " · " + lesson.date.formatLong(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                listOfNotNull(
                    lesson.startTime?.let { start -> lesson.endTime?.let { "$start–$it" } ?: start },
                    lesson.classroom?.let { stringResource(R.string.lesson_classroom, it) },
                    lesson.teacherName,
                ).forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
                stateLabel(lesson)?.let { (text, color) ->
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.SemiBold)
                }
                if (events.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text(
                        stringResource(R.string.lesson_events_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    events.forEach { event -> EventDetail(event) }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

@Composable
private fun EventDetail(event: EventEntity) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(Color(event.color), CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(event.typeName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        if (event.topic.isNotBlank()) {
            Text(event.topic.trim(), style = MaterialTheme.typography.bodyMedium)
        }
        listOfNotNull(event.subjectName, event.teacherName, event.date.formatLong()).forEach {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun stateLabel(lesson: TimetableLessonEntity): Pair<String, Color>? {
    val colors = MaterialTheme.colorScheme
    val other = lesson.otherDate?.formatShort()
    return when (lesson.state) {
        LessonState.NORMAL -> null
        LessonState.CANCELLED -> stringResource(R.string.lesson_cancelled) to colors.error
        LessonState.SUBSTITUTION -> {
            val original = listOfNotNull(lesson.originalSubjectName, lesson.originalTeacherName).joinToString(", ")
            val text = if (original.isEmpty()) {
                stringResource(R.string.lesson_substitution)
            } else {
                stringResource(R.string.lesson_substitution_for, original)
            }
            text to colors.tertiary
        }
        LessonState.MOVED_AWAY -> (
            if (other != null) stringResource(R.string.lesson_moved_to, other, lesson.otherLessonNo ?: 0)
            else stringResource(R.string.lesson_moved)
        ) to colors.error
        LessonState.MOVED_HERE -> (
            if (other != null) stringResource(R.string.lesson_moved_from, other, lesson.otherLessonNo ?: 0)
            else stringResource(R.string.lesson_moved)
        ) to colors.tertiary
    }
}

/** "Kartkówka: Present Simple"; a topic that already starts with the type stands alone. */
private fun eventLabel(event: EventEntity): String {
    val topic = event.topic.trim()
    return when {
        topic.isEmpty() -> event.typeName
        topic.startsWith(event.typeName, ignoreCase = true) -> topic.replaceFirstChar { it.uppercase() }
        else -> "${event.typeName}: $topic"
    }
}
