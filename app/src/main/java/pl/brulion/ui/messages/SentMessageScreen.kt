package pl.brulion.ui.messages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.brulion.R
import pl.brulion.librus.MessageHtml
import pl.brulion.ui.common.ReportButton
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val SentHeaderDate = DateTimeFormatter.ofPattern("d.MM.yyyy, HH:mm")

/** A message the user sent: who got it, when, whether it was read, and what it said. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SentMessageScreen(viewModel: SentMessageViewModel, onBack: () -> Unit) {
    val message by viewModel.message.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.messages_folder_sent)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        val current = message
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (viewModel.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (current == null) return@Column
            Text(
                text = current.subject.ifBlank { stringResource(R.string.messages_no_subject) },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.messages_sent_to, current.recipients.ifBlank { stringResource(R.string.messages_recipient_unknown) }),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = Instant.ofEpochSecond(current.sentAt).atZone(ZoneId.systemDefault()).format(SentHeaderDate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = sentReadStatus(current),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            HorizontalDivider()
            viewModel.error?.let { error ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                    Text(
                        text = stringResource(error.message),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Row {
                    TextButton(onClick = viewModel::refresh) { Text(stringResource(R.string.retry)) }
                    ReportButton("Błąd otwierania wysłanej wiadomości")
                }
            }
            SelectionContainer {
                Text(
                    text = current.body?.let { rememberMessageText(it) } ?: AnnotatedString(MessageHtml.toPlainText(current.preview)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (current.body == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
