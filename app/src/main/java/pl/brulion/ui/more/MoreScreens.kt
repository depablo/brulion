package pl.brulion.ui.more

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pl.brulion.R
import pl.brulion.data.db.AnnouncementEntity
import pl.brulion.data.db.AttendanceCategory
import pl.brulion.data.db.AttendanceEntity
import pl.brulion.data.db.NoteEntity
import pl.brulion.data.db.NotePolarity
import pl.brulion.data.matchesQuery
import pl.brulion.ui.common.EmptyState
import pl.brulion.ui.common.NewBadge
import pl.brulion.ui.common.ProfileDetailScaffold
import pl.brulion.ui.common.SearchField
import pl.brulion.ui.common.appContainer
import pl.brulion.ui.common.formatLong
import pl.brulion.ui.common.formatShort
import pl.brulion.ui.common.formatTimestamp
import pl.brulion.ui.common.isFresh
import pl.brulion.ui.common.rememberToday
import pl.brulion.ui.events.EventRow
import java.time.LocalDate

private val ListPadding = PaddingValues(16.dp)

@Composable
fun AnnouncementsScreen(onBack: () -> Unit) {
    ProfileDetailScaffold(stringResource(R.string.section_announcements), onBack) { profile ->
        val container = appContainer()
        val announcements = container.announcements
        val scope = rememberCoroutineScope()
        val split by remember(profile.id) { announcements.observe(profile.id) }.collectAsStateWithLifecycle(null)
        val words by container.settings.mutedWords.collectAsStateWithLifecycle()
        var showHidden by rememberSaveable { mutableStateOf(false) }
        var editWords by rememberSaveable { mutableStateOf(false) }
        var query by rememberSaveable { mutableStateOf("") }
        val (allVisible, hidden) = split ?: return@ProfileDetailScaffold
        val visible = allVisible.filter { matchesQuery(query, it.subject, it.content, it.authorName) }
        if (allVisible.isEmpty() && hidden.isEmpty()) {
            EmptyState(Icons.Outlined.Campaign, stringResource(R.string.announcements_empty))
            return@ProfileDetailScaffold
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = ListPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "search") {
                SearchField(query = query, onQueryChange = { query = it }, placeholder = stringResource(R.string.announcements_search))
            }
            if (visible.isEmpty()) {
                item { Text(stringResource(R.string.search_nothing), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            items(visible, key = { it.id }) { announcement ->
                AnnouncementCard(announcement) {
                    TextButton(onClick = { scope.launch { announcements.hide(announcement.id) } }) {
                        Text(stringResource(R.string.announcements_hide))
                    }
                }
            }
            if (hidden.isNotEmpty()) {
                item(key = "hidden") {
                    TextButton(onClick = { showHidden = !showHidden }) {
                        Text(stringResource(if (showHidden) R.string.announcements_hidden_collapse else R.string.announcements_hidden_expand, hidden.size))
                    }
                }
            }
            if (showHidden) {
                items(hidden, key = { "h" + it.announcement.id }) { (announcement, mutedBy) ->
                    AnnouncementCard(announcement, dimmed = true) {
                        if (mutedBy == null) {
                            TextButton(onClick = { scope.launch { announcements.show(announcement.id) } }) {
                                Text(stringResource(R.string.announcements_restore))
                            }
                        } else {
                            Text(
                                stringResource(R.string.announcements_muted_by, mutedBy),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item(key = "words") {
                TextButton(onClick = { editWords = true }) {
                    Text(
                        if (words.isEmpty()) stringResource(R.string.announcements_words_none)
                        else stringResource(R.string.announcements_words, words.joinToString(", "))
                    )
                }
            }
        }
        if (editWords) {
            MutedWordsDialog(words, onDismiss = { editWords = false }) {
                container.settings.setMutedWords(it)
                editWords = false
            }
        }
    }
}

@Composable
private fun MutedWordsDialog(words: List<String>, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf(words.joinToString(", ")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.announcements_words_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.announcements_words_hint), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(value = text, onValueChange = { text = it }, placeholder = { Text("matura, liceum, LO") })
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun AnnouncementCard(announcement: AnnouncementEntity, dimmed: Boolean = false, actions: @Composable () -> Unit = {}) {
    var expanded by rememberSaveable(announcement.id) { mutableStateOf(false) }
    Card(
        onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth().animateContentSize().alpha(if (dimmed) 0.6f else 1f),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = announcement.subject,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (isFresh(announcement.firstSeenAt)) NewBadge()
            }
            Text(
                text = listOfNotNull(announcement.authorName, formatTimestamp(announcement.addedAt)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = announcement.content,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
            )
            // Actions only once opened, so the list stays calm.
            if (expanded) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { actions() }
            }
        }
    }
}

@Composable
fun EventsScreen(onBack: () -> Unit) {
    ProfileDetailScaffold(stringResource(R.string.section_events), onBack) { profile ->
        val db = appContainer().database
        val items by remember(profile.id) { db.eventDao().observeAll(profile.id) }.collectAsStateWithLifecycle(null)
        val list = items ?: return@ProfileDetailScaffold
        if (list.isEmpty()) {
            EmptyState(Icons.Outlined.EventAvailable, stringResource(R.string.events_empty))
            return@ProfileDetailScaffold
        }
        val today = rememberToday()
        val (upcoming, past) = list.partition { it.date >= today }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = ListPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (upcoming.isEmpty()) {
                item { Text(stringResource(R.string.events_none_upcoming), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            upcoming.groupBy { it.date }.forEach { (date, events) ->
                item(key = "d$date") { DateHeader(date) }
                items(events, key = { it.id }) { EventRow(it, isNew = isFresh(it.firstSeenAt)) }
            }
            if (past.isNotEmpty()) {
                item(key = "past") {
                    Text(
                        text = stringResource(R.string.events_past),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                past.asReversed().groupBy { it.date }.forEach { (date, events) ->
                    item(key = "p$date") { DateHeader(date) }
                    items(events, key = { it.id }) { EventRow(it) }
                }
            }
        }
    }
}

@Composable
private fun DateHeader(date: LocalDate) {
    Text(
        text = date.formatLong(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AttendanceScreen(onBack: () -> Unit) {
    ProfileDetailScaffold(stringResource(R.string.section_attendance), onBack) { profile ->
        val db = appContainer().database
        val items by remember(profile.id) { db.attendanceDao().observeAll(profile.id) }.collectAsStateWithLifecycle(null)
        val list = items ?: return@ProfileDetailScaffold
        if (list.isEmpty()) {
            EmptyState(Icons.Outlined.FactCheck, stringResource(R.string.attendance_empty))
            return@ProfileDetailScaffold
        }
        val counts = list.groupingBy { it.category }.eachCount()
        val notPresent = list.filter { it.category != AttendanceCategory.PRESENT }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = ListPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        attendancePercent(counts)?.let {
                            Text(
                                text = stringResource(R.string.attendance_percent, it),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (category in AttendanceCategory.entries) {
                                if (category == AttendanceCategory.PRESENT) continue
                                val count = counts[category] ?: continue
                                Text("${stringResource(category.label)}: $count", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
            if (notPresent.isEmpty()) {
                item { Text(stringResource(R.string.attendance_all_present), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            notPresent.groupBy { it.date }.forEach { (date, entries) ->
                item(key = "d$date") { DateHeader(date) }
                items(entries, key = { it.id }) { AttendanceRow(it) }
            }
        }
    }
}

/** Share of lessons attended (late and released count as attended); null without presence records. */
private fun attendancePercent(counts: Map<AttendanceCategory, Int>): Int? {
    val present = counts[AttendanceCategory.PRESENT] ?: return null
    val attended = present + (counts[AttendanceCategory.LATE] ?: 0) + (counts[AttendanceCategory.RELEASED] ?: 0)
    return attended * 100 / counts.values.sum()
}

@Composable
private fun AttendanceRow(entry: AttendanceEntity) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(40.dp)
                .background(entry.typeColor?.let { Color(it) } ?: MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(6.dp))
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(entry.typeShort, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = Color.Black)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = listOfNotNull(entry.lessonNo?.let { stringResource(R.string.attendance_lesson, it) }, entry.subjectName)
                    .joinToString(" · ")
                    .ifEmpty { entry.typeName },
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(entry.typeName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (isFresh(entry.firstSeenAt)) NewBadge()
    }
}

private val AttendanceCategory.label: Int
    get() = when (this) {
        AttendanceCategory.PRESENT -> R.string.attendance_present
        AttendanceCategory.ABSENT -> R.string.attendance_absent
        AttendanceCategory.EXCUSED -> R.string.attendance_excused
        AttendanceCategory.LATE -> R.string.attendance_late
        AttendanceCategory.RELEASED -> R.string.attendance_released
        AttendanceCategory.OTHER -> R.string.attendance_other
    }

@Composable
fun NotesScreen(onBack: () -> Unit) {
    ProfileDetailScaffold(stringResource(R.string.section_notes), onBack) { profile ->
        val db = appContainer().database
        val items by remember(profile.id) { db.noteDao().observeAll(profile.id) }.collectAsStateWithLifecycle(null)
        val list = items ?: return@ProfileDetailScaffold
        if (list.isEmpty()) {
            EmptyState(Icons.Outlined.StickyNote2, stringResource(R.string.notes_empty))
            return@ProfileDetailScaffold
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = ListPadding, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(list, key = { it.id }) { NoteCard(it) }
        }
    }
}

@Composable
private fun NoteCard(note: NoteEntity) {
    val accent = when (note.polarity) {
        NotePolarity.POSITIVE -> Color(0xFF3FA66B)
        NotePolarity.NEGATIVE -> MaterialTheme.colorScheme.error
        NotePolarity.NEUTRAL -> MaterialTheme.colorScheme.outline
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(accent))
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = listOfNotNull(note.categoryName, note.date.formatShort()).joinToString(" · "),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (isFresh(note.firstSeenAt)) NewBadge()
                }
                Text(note.text, style = MaterialTheme.typography.bodyLarge)
                note.teacherName?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
