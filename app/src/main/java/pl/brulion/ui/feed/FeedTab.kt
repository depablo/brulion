package pl.brulion.ui.feed

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.brulion.R
import pl.brulion.data.childKey
import pl.brulion.data.db.FeedEntryEntity
import pl.brulion.data.db.FeedKind
import pl.brulion.data.db.ProfileEntity
import pl.brulion.librus.schoolToday
import pl.brulion.ui.common.EmptyState
import pl.brulion.ui.common.appContainer
import pl.brulion.ui.common.avatarColor
import pl.brulion.ui.common.epochToLocal
import pl.brulion.ui.common.formatLong
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val Time = DateTimeFormatter.ofPattern("HH:mm")

/** Activity of all children, newest first; it keeps entries even after Librus changes them. */
@Composable
fun FeedTab(profiles: List<ProfileEntity>, onOpen: (FeedEntryEntity) -> Unit) {
    val db = appContainer().database
    val entries by remember { db.feedDao().observeRecent() }.collectAsStateWithLifecycle(null)
    val announcements = appContainer().announcements
    val hiddenAnnouncements by remember { announcements.observeHiddenIds() }.collectAsStateWithLifecycle(emptySet())
    val list = entries?.filterNot { it.kind == FeedKind.ANNOUNCEMENT && it.refKey in hiddenAnnouncements } ?: return
    val children = remember(profiles) {
        profiles.sortedBy { it.studentName }.distinctBy { childKey(it) }.associate { childKey(it) to it.studentName }
    }
    var child by rememberSaveable { mutableStateOf<String?>(null) }
    // School-wide things (announcements, letters to all parents) reach every child; show them once.
    val shown = list.filter { child == null || it.childKey == child }
        .groupBy { listOf(it.kind, it.title, it.detail, it.occurredAt) }
        .values
        .map { same -> FeedItem(same.first(), same.map { it.childKey }.distinct()) }

    if (list.isEmpty()) {
        EmptyState(Icons.Outlined.DynamicFeed, stringResource(R.string.feed_empty))
        return
    }
    val today = schoolToday()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 16.dp)) {
        if (children.size > 1) {
            item {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = child == null, onClick = { child = null }, label = { Text(stringResource(R.string.feed_all)) })
                    children.forEach { (key, name) ->
                        FilterChip(selected = child == key, onClick = { child = key }, label = { Text(name.substringBefore(' ')) })
                    }
                }
            }
        }
        if (shown.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.feed_empty_child),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        shown.groupBy { epochToLocal(it.entry.occurredAt).toLocalDate() }.forEach { (day, dayEntries) ->
            item(key = "d$day") {
                Text(
                    text = when (day) {
                        today -> stringResource(R.string.tab_today)
                        today.minusDays(1) -> stringResource(R.string.feed_yesterday)
                        else -> day.formatLong()
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                )
            }
            items(dayEntries, key = { it.entry.id }) { item ->
                val names = item.childKeys.mapNotNull { children[it] }
                FeedRow(item.entry, names, showChild = children.size > 1, onClick = { onOpen(item.entry) })
            }
        }
    }
}

@Composable
private fun FeedRow(entry: FeedEntryEntity, childNames: List<String>, showChild: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = epochToLocal(entry.occurredAt).format(Time),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.widthIn(min = 40.dp).padding(top = 2.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showChild) {
                    childNames.forEach { name ->
                        Box(Modifier.size(10.dp).background(avatarColor(name), CircleShape))
                        Spacer(Modifier.width(3.dp))
                    }
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = childNames.joinToString(", ") { it.substringBefore(' ') },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Icon(entry.kind.icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
            }
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            entry.detail?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (entry.kind == FeedKind.EVENT && entry.date != null && !entry.date.isBefore(schoolToday())) {
                TextButton(onClick = { addToCalendar(context, entry, childNames) }, contentPadding = PaddingValues(0.dp)) {
                    Text(stringResource(R.string.feed_add_to_calendar))
                }
            }
        }
    }
}

/** A feed line, possibly shared by several children. */
private class FeedItem(val entry: FeedEntryEntity, val childKeys: List<String>)

private val FeedKind.icon: ImageVector
    get() = when (this) {
        FeedKind.GRADE -> Icons.Outlined.School
        FeedKind.NOTE -> Icons.Outlined.StickyNote2
        FeedKind.ATTENDANCE -> Icons.Outlined.FactCheck
        FeedKind.EVENT -> Icons.Outlined.Event
        FeedKind.ANNOUNCEMENT -> Icons.Outlined.Campaign
        FeedKind.MESSAGE -> Icons.Outlined.Mail
        FeedKind.LESSON_CHANGE -> Icons.Outlined.EventBusy
    }

/** Opens the calendar app with an all-day entry prefilled; nothing is saved without the user. */
fun addToCalendar(context: Context, entry: FeedEntryEntity, childNames: List<String>) {
    val date = entry.date ?: return
    val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val intent = Intent(Intent.ACTION_INSERT)
        .setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, start + 24 * 60 * 60 * 1000L)
        .putExtra(
            CalendarContract.Events.TITLE,
            childNames.joinToString(", ") { it.substringBefore(' ') } + ": " + entry.title.substringBeforeLast(','),
        )
        .putExtra(CalendarContract.Events.DESCRIPTION, entry.detail.orEmpty())
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.feed_no_calendar, Toast.LENGTH_LONG).show()
    }
}
