package pl.brulion.ui.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Attachment
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
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
import pl.brulion.data.childKey
import pl.brulion.data.db.InboxMessage
import pl.brulion.data.db.MessageAudience
import pl.brulion.data.db.ProfileEntity
import pl.brulion.data.matchesQuery
import pl.brulion.librus.MessageHtml
import pl.brulion.ui.common.EmptyState
import pl.brulion.ui.common.NameAvatar
import pl.brulion.ui.common.SearchField
import pl.brulion.ui.common.appContainer
import pl.brulion.ui.common.formatTimestamp

@Composable
fun MessagesTab(profileId: Long, onOpen: (messageId: Long) -> Unit, onCompose: () -> Unit) {
    val db = appContainer().database
    val messages by remember(profileId) { db.messageDao().observeInbox(profileId) }.collectAsStateWithLifecycle(null)
    val profiles by appContainer().accounts.allProfiles.collectAsStateWithLifecycle(emptyList())
    val bodies by remember(profileId) { db.messageDao().observeBodies(profileId) }.collectAsStateWithLifecycle(emptyList())
    var query by rememberSaveable(profileId) { mutableStateOf("") }
    val all = messages ?: return
    // Bodies are only known for messages opened or fetched in the background.
    val bodyOf = remember(bodies) { bodies.associate { it.messageId to it.body } }
    val list = remember(all, bodyOf, query) {
        all.filter { matchesQuery(query, it.message.senderName, it.message.subject, it.message.preview, bodyOf[it.message.id]) }
    }
    Box(Modifier.fillMaxSize()) {
        if (all.isEmpty()) {
            EmptyState(Icons.Outlined.MarkEmailRead, stringResource(R.string.messages_empty))
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 88.dp)) {
                item(key = "search") {
                    SearchField(
                        query = query,
                        onQueryChange = { query = it },
                        placeholder = stringResource(R.string.messages_search),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                if (list.isEmpty()) {
                    item { Text(stringResource(R.string.search_nothing), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(list, key = { it.message.id }) { item ->
                    MessageRow(item, profiles, Modifier.clickable { onOpen(item.message.id) })
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onCompose,
            icon = { Icon(Icons.Outlined.Edit, null) },
            text = { Text(stringResource(R.string.compose_title)) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Composable
fun MessageRow(item: InboxMessage, profiles: List<ProfileEntity>, modifier: Modifier = Modifier) {
    val message = item.message
    val unread = message.readAt == null
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
        NameAvatar(message.senderName, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = message.senderName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                AudienceBadge(item.audience)
                Text(
                    text = formatTimestamp(message.sentAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = message.subject,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Normal,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (message.hasAttachments) {
                    Icon(
                        Icons.Outlined.Attachment,
                        contentDescription = stringResource(R.string.messages_attachment),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (unread) {
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                }
            }
            alsoInText(item, profiles)?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            if (message.preview.isNotBlank()) {
                Text(
                    text = MessageHtml.toPlainText(message.preview).replace('\n', ' '),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Short hint next to the date: written to me only, or to how many people. */
@Composable
private fun AudienceBadge(audience: MessageAudience) {
    val icon = when (audience) {
        MessageAudience.Direct -> Icons.Outlined.Person
        is MessageAudience.Group, MessageAudience.Several -> Icons.Outlined.Groups
        MessageAudience.Unknown -> return
    }
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.padding(start = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = audienceText(audience), modifier = Modifier.size(14.dp), tint = color)
        val label = when (audience) {
            MessageAudience.Direct -> stringResource(R.string.audience_direct_short)
            is MessageAudience.Group -> audience.count.toString()
            else -> null
        }
        if (label != null) {
            Spacer(Modifier.width(3.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}

@Composable
fun audienceText(audience: MessageAudience): String? = when (audience) {
    MessageAudience.Direct -> stringResource(R.string.audience_direct)
    is MessageAudience.Group -> stringResource(R.string.audience_group, audience.count)
    MessageAudience.Several -> stringResource(R.string.audience_several)
    MessageAudience.Unknown -> null
}

/**
 * "Też u: Ola, Zosia" when the same message reached the user's other children; the
 * other account of the same child doesn't count.
 */
@Composable
fun alsoInText(item: InboxMessage, profiles: List<ProfileEntity>): String? {
    if (item.alsoIn.isEmpty()) return null
    val byId = profiles.associateBy { it.id }
    val own = byId[item.message.profileId]?.let(::childKey)
    val names = item.alsoIn.mapNotNull(byId::get)
        .filter { childKey(it) != own }
        .distinctBy(::childKey)
        .map { it.studentName.substringBefore(' ') }
    if (names.isEmpty()) return null
    return stringResource(R.string.messages_also_in, names.joinToString(", "))
}
