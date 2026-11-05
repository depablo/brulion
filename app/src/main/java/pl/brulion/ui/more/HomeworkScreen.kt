package pl.brulion.ui.more

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.brulion.R
import pl.brulion.data.db.HomeworkEntity
import pl.brulion.ui.common.EmptyState
import pl.brulion.ui.common.NewBadge
import pl.brulion.ui.common.ProfileDetailScaffold
import pl.brulion.ui.common.appContainer
import pl.brulion.ui.common.formatShort
import pl.brulion.ui.common.isFresh
import pl.brulion.ui.common.rememberToday

@Composable
fun HomeworkScreen(onBack: () -> Unit) {
    ProfileDetailScaffold(stringResource(R.string.section_homework), onBack) { profile ->
        val db = appContainer().database
        val items by remember(profile.id) { db.homeworkDao().observeAll(profile.id) }.collectAsStateWithLifecycle(null)
        val list = items ?: return@ProfileDetailScaffold
        if (list.isEmpty()) {
            EmptyState(Icons.Outlined.Assignment, stringResource(R.string.homework_empty))
            return@ProfileDetailScaffold
        }
        val today = rememberToday()
        // Coming up first, soonest due first; then what's past, most recent first.
        val (upcoming, past) = list.partition { it.dueDate >= today }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (upcoming.isEmpty()) {
                item {
                    Text(stringResource(R.string.homework_none_upcoming), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            upcoming.sortedWith(compareBy({ it.dueDate }, { it.id })).groupBy { it.dueDate }.forEach { (date, group) ->
                item(key = "ud$date") { DateHeader(date) }
                items(group, key = { "u${it.id}" }) { HomeworkRow(it, isNew = isFresh(it.firstSeenAt)) }
            }
            if (past.isNotEmpty()) {
                item(key = "past") {
                    Text(
                        text = stringResource(R.string.homework_past),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                past.groupBy { it.dueDate }.forEach { (date, group) ->
                    item(key = "pd$date") { DateHeader(date) }
                    items(group, key = { "p${it.id}" }) { HomeworkRow(it) }
                }
            }
        }
    }
}

/** One assignment; tap to show the whole text when it's longer than a few lines. */
@Composable
private fun HomeworkRow(homework: HomeworkEntity, isNew: Boolean = false) {
    var expanded by rememberSaveable(homework.id) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = homework.content.isNotBlank()) { expanded = !expanded }
            .animateContentSize(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = homework.subjectName ?: stringResource(R.string.homework_no_subject),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (isNew) {
                Spacer(Modifier.width(8.dp))
                NewBadge()
            }
        }
        val meta = listOfNotNull(
            homework.teacherName,
            homework.addedDate?.let { stringResource(R.string.homework_added, it.formatShort()) },
        ).joinToString(" · ")
        if (meta.isNotBlank()) {
            Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (homework.topic.isNotBlank()) {
            Text(homework.topic, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
        if (homework.content.isNotBlank()) {
            Text(
                text = homework.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
