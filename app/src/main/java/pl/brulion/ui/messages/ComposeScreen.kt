package pl.brulion.ui.messages

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pl.brulion.R
import pl.brulion.ui.common.ReportButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(viewModel: ComposeViewModel, onClose: () -> Unit) {
    val context = LocalContext.current
    var confirm by rememberSaveable { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(viewModel.sent) {
        if (viewModel.sent) {
            Toast.makeText(context, R.string.compose_sent, Toast.LENGTH_SHORT).show()
            onClose()
        }
    }

    // Leaving now wouldn't stop the message, but it would hide whether it went through.
    BackHandler(enabled = viewModel.sending) {}

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.compose_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose, enabled = !viewModel.sending) { Icon(Icons.Default.Close, stringResource(R.string.cancel)) }
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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
        ) {
            item {
                if (viewModel.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                viewModel.error?.let { error ->
                    androidx.compose.foundation.layout.Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                        Text(
                            text = stringResource(error.message) + (error.detail?.let { " ($it)" } ?: ""),
                            color = MaterialTheme.colorScheme.error,
                        )
                        Row {
                            if (viewModel.groups == null) TextButton(onClick = viewModel::loadGroups) { Text(stringResource(R.string.retry)) }
                            ReportButton("Błąd pisania wiadomości")
                        }
                    }
                }
            }
            viewModel.groups?.let { groups ->
                item {
                    Text(
                        text = stringResource(R.string.compose_to),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 12.dp),
                    )
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        groups.forEach { group ->
                            FilterChip(
                                selected = viewModel.group == group,
                                onClick = {
                                    filter = ""
                                    viewModel.selectGroup(group)
                                },
                                label = { Text(group.label) },
                            )
                        }
                    }
                }
            }
            viewModel.recipients?.let { recipients ->
                if (recipients.size > 8) {
                    item {
                        OutlinedTextField(
                            value = filter,
                            onValueChange = { filter = it },
                            placeholder = { Text(stringResource(R.string.compose_search)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        )
                    }
                }
                val shown = recipients.filter { filter.isBlank() || it.name.contains(filter.trim(), ignoreCase = true) }
                if (recipients.isEmpty()) {
                    item { Text(stringResource(R.string.compose_no_recipients), Modifier.padding(16.dp)) }
                }
                items(shown, key = { it.id }) { recipient ->
                    ListItem(
                        headlineContent = { Text(recipient.name) },
                        leadingContent = {
                            Checkbox(checked = recipient in viewModel.selected, onCheckedChange = { viewModel.toggle(recipient) })
                        },
                        modifier = Modifier.clickable { viewModel.toggle(recipient) },
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = viewModel.topic,
                    onValueChange = { viewModel.topic = it.take(MAX_TOPIC) },
                    label = { Text(stringResource(R.string.compose_topic)) },
                    singleLine = true,
                    enabled = !viewModel.sending,
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
                )
                OutlinedTextField(
                    value = viewModel.text,
                    onValueChange = { viewModel.text = it.take(MAX_LENGTH) },
                    label = { Text(stringResource(R.string.compose_body)) },
                    enabled = !viewModel.sending,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp).padding(16.dp),
                )
            }
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.compose_confirm_title)) },
            text = { Text(stringResource(R.string.reply_confirm_text, viewModel.selected.joinToString(", ") { it.name })) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = false
                    viewModel.send()
                }) { Text(stringResource(R.string.reply_send)) }
            },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private const val MAX_TOPIC = 200
/** Synergia's own limit for a message body. */
private const val MAX_LENGTH = 20_000
