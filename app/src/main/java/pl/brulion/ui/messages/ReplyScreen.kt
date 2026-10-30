package pl.brulion.ui.messages

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.brulion.R
import pl.brulion.ui.common.ReportButton
import pl.brulion.ui.common.appContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplyScreen(viewModel: ReplyViewModel, profileId: Long, messageId: Long, onClose: () -> Unit) {
    val context = LocalContext.current
    val container = appContainer()
    val message by remember(profileId, messageId) { container.database.messageDao().observe(profileId, messageId) }
        .collectAsStateWithLifecycle(null)
    var confirm by rememberSaveable { mutableStateOf(false) }
    val sending = viewModel.sending

    LaunchedEffect(viewModel.sent) {
        if (viewModel.sent) {
            Toast.makeText(context, R.string.reply_sent, Toast.LENGTH_SHORT).show()
            onClose()
        }
    }
    // Leaving now wouldn't stop the reply, but it would hide whether it went through.
    BackHandler(enabled = sending) {}

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.message_reply)) },
                navigationIcon = {
                    IconButton(onClick = onClose, enabled = !sending) { Icon(Icons.Default.Close, stringResource(R.string.cancel)) }
                },
                actions = {
                    if (sending) {
                        CircularProgressIndicator(Modifier.padding(12.dp).size(24.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { confirm = true }, enabled = viewModel.text.isNotBlank()) {
                            Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.reply_send))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            message?.let {
                Text(stringResource(R.string.reply_to, it.senderName), style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = stringResource(R.string.reply_subject, it.subject),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            viewModel.error?.let {
                val detail = it.detail?.let { detail -> " ($detail)" }.orEmpty()
                Text(stringResource(it.message) + detail, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                ReportButton("Błąd wysyłania odpowiedzi")
            }
            OutlinedTextField(
                value = viewModel.text,
                onValueChange = { if (it.length <= MAX_LENGTH) viewModel.text = it },
                modifier = Modifier.fillMaxWidth().weight(1f),
                placeholder = { Text(stringResource(R.string.reply_hint)) },
                enabled = !sending,
            )
        }
    }

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(stringResource(R.string.reply_confirm_title)) },
            text = { Text(stringResource(R.string.reply_confirm_text, message?.senderName.orEmpty())) },
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

/** Synergia's own limit for a message body. */
private const val MAX_LENGTH = 20_000
