package pl.brulion.ui.justifications

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.brulion.R
import pl.brulion.data.db.AttendanceEntity
import pl.brulion.data.justifications.JustificationMode
import pl.brulion.ui.common.formatLong
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

private enum class DatePick { DATE, FROM, TO }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JustificationScreen(viewModel: JustificationViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    var confirm by rememberSaveable { mutableStateOf(false) }
    var picking by rememberSaveable { mutableStateOf<DatePick?>(null) }

    LaunchedEffect(viewModel.sent) {
        if (viewModel.sent) {
            Toast.makeText(context, R.string.justification_sent, Toast.LENGTH_SHORT).show()
            onClose()
        }
    }

    // Leaving now wouldn't stop the request, but it would hide whether it went through.
    BackHandler(enabled = viewModel.sending) {}

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.justification_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose, enabled = !viewModel.sending) {
                        Icon(Icons.Default.Close, stringResource(R.string.cancel))
                    }
                },
                actions = {
                    if (viewModel.sending) {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { confirm = true }, enabled = viewModel.canSend) {
                            Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.reply_send))
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                if (viewModel.sending) LinearProgressIndicator(Modifier.fillMaxWidth())
                viewModel.error?.let { error ->
                    Text(
                        text = stringResource(error.message) + (error.detail?.let { " ($it)" } ?: ""),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = viewModel.mode == JustificationMode.LESSONS,
                        onClick = { viewModel.selectMode(JustificationMode.LESSONS) },
                        label = { Text(stringResource(R.string.justification_mode_lessons)) },
                    )
                    FilterChip(
                        selected = viewModel.mode == JustificationMode.DAYS,
                        onClick = { viewModel.selectMode(JustificationMode.DAYS) },
                        label = { Text(stringResource(R.string.justification_mode_days)) },
                    )
                }
            }
            when (viewModel.mode) {
                JustificationMode.LESSONS -> lessonsSection(viewModel, onPickDate = { picking = DatePick.DATE })
                JustificationMode.DAYS -> daysSection(viewModel, onPickDate = { picking = it })
            }
            item {
                OutlinedTextField(
                    value = viewModel.message,
                    onValueChange = { viewModel.message = it },
                    label = { Text(stringResource(R.string.justification_message)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (viewModel.canNotify) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.justification_notify), Modifier.weight(1f))
                        Switch(
                            checked = viewModel.notifyTeachers,
                            onCheckedChange = { viewModel.notifyTeachers = it },
                        )
                    }
                }
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.justification_confirm_title)) },
            text = { Text(stringResource(R.string.justification_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    viewModel.send()
                }) { Text(stringResource(R.string.reply_send)) }
            },
            dismissButton = {
                TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    picking?.let { target ->
        val initial = when (target) {
            DatePick.DATE -> viewModel.date
            DatePick.FROM -> viewModel.from
            DatePick.TO -> viewModel.to
        }
        DatePickerFor(
            initial = initial,
            onPicked = { picked ->
                when (target) {
                    DatePick.DATE -> viewModel.pickDate(picked)
                    DatePick.FROM -> viewModel.pickFrom(picked)
                    DatePick.TO -> viewModel.pickTo(picked)
                }
            },
            onDismiss = { picking = null },
        )
    }
}

private fun LazyListScope.lessonsSection(
    viewModel: JustificationViewModel,
    onPickDate: () -> Unit,
) {
    item {
        DateRow(stringResource(R.string.justification_day), viewModel.date, onClick = onPickDate)
    }
    val candidates = viewModel.lessonCandidates
    if (candidates.isEmpty()) {
        item { Text(stringResource(R.string.justification_no_entries), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    } else {
        item { Text(stringResource(R.string.justification_lessons), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary) }
        items(candidates, key = { it.id }) { entry -> LessonRow(entry, viewModel) }
    }
    if (viewModel.lessons.isEmpty() && candidates.isNotEmpty()) {
        item { Text(stringResource(R.string.justification_pick_lessons), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

private fun LazyListScope.daysSection(
    viewModel: JustificationViewModel,
    onPickDate: (DatePick) -> Unit,
) {
    item { DateRow(stringResource(R.string.justification_from), viewModel.from, onClick = { onPickDate(DatePick.FROM) }) }
    item { DateRow(stringResource(R.string.justification_to), viewModel.to, onClick = { onPickDate(DatePick.TO) }) }
    if (viewModel.to.isBefore(viewModel.from)) {
        item { Text(stringResource(R.string.justification_bad_range), color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun LessonRow(entry: AttendanceEntity, viewModel: JustificationViewModel) {
    val lessonNo = entry.lessonNo ?: return
    ListItem(
        headlineContent = { Text(listOfNotNull(stringResource(R.string.attendance_lesson, lessonNo), entry.subjectName).joinToString(" · ")) },
        supportingContent = { Text(entry.typeName) },
        leadingContent = {
            Checkbox(checked = lessonNo in viewModel.lessons, onCheckedChange = null)
        },
        modifier = Modifier.clickable { viewModel.toggleLesson(lessonNo) },
    )
}

@Composable
private fun DateRow(label: String, date: LocalDate, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        OutlinedButton(onClick = onClick) { Text(date.formatLong()) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerFor(initial: LocalDate, onPicked: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    // DatePicker works in UTC midnights, so the conversion has to use UTC too.
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { millis ->
                    onPicked(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                }
                onDismiss()
            }) { Text(stringResource(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) {
        DatePicker(state = state)
    }
}
