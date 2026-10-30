package pl.brulion.ui.messages

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Attachment
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.brulion.R
import pl.brulion.data.Downloads
import pl.brulion.librus.MessageHtml
import pl.brulion.ui.common.NameAvatar
import pl.brulion.ui.common.ReportButton
import pl.brulion.ui.common.appContainer
import pl.brulion.ui.common.epochToLocal
import java.io.File
import java.time.format.DateTimeFormatter

private val HeaderDate = DateTimeFormatter.ofPattern("d.MM.yyyy, HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageScreen(viewModel: MessageViewModel, onBack: () -> Unit, onReply: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val message by viewModel.message.collectAsStateWithLifecycle()
    val profiles by appContainer().accounts.allProfiles.collectAsStateWithLifecycle(emptyList())
    val body by viewModel.body.collectAsStateWithLifecycle()
    val attachments by viewModel.attachments.collectAsStateWithLifecycle()

    // Confirm where a finished download went.
    viewModel.downloads.forEach { (id, state) ->
        if (state is MessageViewModel.Download.Ready && state.savedTo != null) {
            LaunchedEffect(id, state) {
                Toast.makeText(context, resources.getString(R.string.message_saved_downloads, state.file.name), Toast.LENGTH_SHORT).show()
            }
        }
    }
    var toSave by remember { mutableStateOf<File?>(null) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val file = toSave
        if (uri != null && file != null) {
            val saved = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
            }.isSuccess
            Toast.makeText(context, if (saved) R.string.message_saved else R.string.message_save_failed, Toast.LENGTH_SHORT).show()
        }
        toSave = null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.section_messages)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
        floatingActionButton = {
            // Automatic notices say themselves not to answer them.
            if (body?.canReply == true && message?.message?.automatic != true) {
                ExtendedFloatingActionButton(
                    onClick = onReply,
                    icon = { Icon(Icons.AutoMirrored.Filled.Reply, null) },
                    text = { Text(stringResource(R.string.message_reply)) },
                )
            }
        },
    ) { padding ->
        val item = message
        val current = item?.message
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (viewModel.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (current == null) return@Column
            Text(current.subject, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                NameAvatar(current.senderName, size = 40.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(current.senderName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    Text(
                        text = epochToLocal(current.sentAt).format(HeaderDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    audienceText(item.audience)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    alsoInText(item, profiles)?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            HorizontalDivider()
            viewModel.error?.let { error ->
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.ErrorOutline, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(error.message),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Row {
                        TextButton(onClick = viewModel::refresh) { Text(stringResource(R.string.retry)) }
                        ReportButton("Błąd otwierania wiadomości")
                    }
                }
            }
            SelectionContainer {
                Text(
                    text = body?.body?.let { rememberMessageText(it) } ?: AnnotatedString(MessageHtml.toPlainText(current.preview)),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (body == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
            }
            if (attachments.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.message_attachments),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
                for (attachment in attachments) {
                    val state = viewModel.downloads[attachment.id]
                    ListItem(
                        headlineContent = { Text(attachment.fileName) },
                        supportingContent = (state as? MessageViewModel.Download.Failed)?.let {
                            { Text(stringResource(it.error.message), color = MaterialTheme.colorScheme.error) }
                        },
                        leadingContent = { Icon(Icons.Outlined.Attachment, null) },
                        trailingContent = {
                            when (state) {
                                MessageViewModel.Download.Running ->
                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                is MessageViewModel.Download.Ready -> Row {
                                    // Without MediaStore (Android 9 and older) the user picks where to save.
                                    if (state.savedTo == null) {
                                        IconButton(onClick = {
                                            toSave = state.file
                                            saveLauncher.launch(state.file.name)
                                        }) { Icon(Icons.Outlined.SaveAlt, stringResource(R.string.message_save)) }
                                    }
                                    IconButton(onClick = { shareFile(context, state.file) }) {
                                        Icon(Icons.Outlined.Share, stringResource(R.string.message_share))
                                    }
                                    IconButton(onClick = { openFile(context, state) }) {
                                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, stringResource(R.string.message_open))
                                    }
                                }
                                else -> Icon(Icons.Outlined.Download, stringResource(R.string.message_download))
                            }
                        },
                        modifier = Modifier.clickable { viewModel.download(attachment) },
                    )
                }
            }
        }
    }
}

/** Renders the HTML some notices contain, with clickable links; plain text stays as is. */
@Composable
private fun rememberMessageText(raw: String): AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    return remember(raw, linkColor) {
        // Bodies stored before envelopes were unwrapped show until the message is fetched again.
        val body = MessageHtml.unwrap(raw)
        if (!MessageHtml.isHtml(body)) {
            AnnotatedString(body)
        } else {
            AnnotatedString.fromHtml(
                htmlString = MessageHtml.toHtml(body),
                linkStyles = TextLinkStyles(SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)),
            )
        }
    }
}

private fun openFile(context: Context, download: MessageViewModel.Download.Ready) {
    val file = download.file
    val uri = download.savedTo ?: FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val type = Downloads.mimeType(file) ?: "*/*"
    val view = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, type)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        // No chooser: with it a missing viewer only shows an empty sheet instead of an exception.
        context.startActivity(view)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, R.string.message_no_viewer, Toast.LENGTH_LONG).show()
    }
}

/** Any app can take the file: mail, messengers, cloud drives. */
private fun shareFile(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType(Downloads.mimeType(file) ?: "application/octet-stream")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    // The chooser shows a preview and passes the grant on only through ClipData.
    send.clipData = ClipData.newRawUri(file.name, uri)
    context.startActivity(Intent.createChooser(send, file.name))
}
