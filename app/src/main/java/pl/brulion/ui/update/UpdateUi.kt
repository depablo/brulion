package pl.brulion.ui.update

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import pl.brulion.BuildConfig
import pl.brulion.R
import pl.brulion.data.update.UpdateManager
import pl.brulion.data.update.UpdateManager.State
import pl.brulion.data.update.UpdateWorker
import pl.brulion.ui.common.appContainer

/** The "Aktualizacje" part of Settings. */
@Composable
fun UpdateSettings() {
    val context = LocalContext.current
    val container = appContainer()
    val updates = container.updates
    val state by updates.state.collectAsStateWithLifecycle()
    val autoCheck by container.settings.updateAutoCheck.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    ListItem(
        headlineContent = { Text(stringResource(R.string.update_version, BuildConfig.VERSION_NAME)) },
        supportingContent = {
            Text(
                when {
                    !updates.isConfigured -> stringResource(R.string.update_not_configured)
                    else -> statusText(state)
                }
            )
        },
        leadingContent = { Icon(Icons.Outlined.SystemUpdate, null) },
        trailingContent = { if (state == State.Checking) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) },
        modifier = Modifier.clickable(enabled = updates.isConfigured && state !is State.Downloading && state !is State.Installing) {
            if (state is State.Available) {
                updates.promptRequest.value = true
            } else {
                scope.launch {
                    // A newer version opens the dialog right away; errors are shown through the state.
                    if (runCatching { updates.check() }.getOrNull() != null) updates.promptRequest.value = true
                }
            }
        },
    )
    if (updates.isConfigured) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.update_auto_check)) },
            supportingContent = { Text(stringResource(R.string.update_auto_check_description)) },
            leadingContent = { Icon(Icons.Outlined.Update, null) },
            trailingContent = {
                Switch(checked = autoCheck, onCheckedChange = {
                    container.settings.setUpdateAutoCheck(it)
                    UpdateWorker.schedule(context, it)
                })
            },
        )
    }
}

@Composable
private fun statusText(state: State): String = when (state) {
    State.Idle -> stringResource(R.string.update_tap_to_check)
    State.Checking -> stringResource(R.string.update_checking)
    State.UpToDate -> stringResource(R.string.update_up_to_date)
    is State.Available -> stringResource(R.string.update_available, state.info.versionName)
    is State.Downloading -> stringResource(R.string.update_downloading)
    is State.Installing -> stringResource(R.string.update_installing)
    is State.Failed -> state.message
}

/** Dialog with the changelog and install progress; shown on request (notification, Settings). */
@Composable
fun UpdatePrompt() {
    val context = LocalContext.current
    val container = appContainer()
    val updates: UpdateManager = container.updates
    val requested by updates.promptRequest.collectAsStateWithLifecycle()
    val state by updates.state.collectAsStateWithLifecycle()
    val pendingConfirmation by updates.pendingConfirmation.collectAsStateWithLifecycle()
    var canInstall by remember { mutableStateOf(updates.canInstall()) }

    // The user may come back from system settings with installing allowed.
    LifecycleResumeEffect(Unit) {
        canInstall = updates.canInstall()
        onPauseOrDispose {}
    }

    val info = when (val s = state) {
        is State.Available -> s.info
        is State.Downloading -> s.info
        is State.Installing -> s.info
        is State.Failed -> s.info
        else -> null
    }
    if (!requested || info == null) return
    // While installing the system screen may wait behind the app; the user can go back to it or close this.
    val busy = state is State.Downloading
    val waitingForSystem = state is State.Installing && pendingConfirmation != null
    val dismiss = { if (!busy) updates.promptRequest.value = false }

    AlertDialog(
        onDismissRequest = dismiss,
        icon = { Icon(Icons.Outlined.SystemUpdate, null) },
        title = { Text(stringResource(R.string.update_dialog_title, info.versionName)) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                if (info.changelog.isNotBlank()) Text(info.changelog, style = MaterialTheme.typography.bodyMedium)
                when (val s = state) {
                    is State.Downloading -> {
                        Text(stringResource(R.string.update_downloading), Modifier.padding(top = 16.dp, bottom = 8.dp))
                        val progress = s.progress
                        if (progress != null) LinearProgressIndicator({ progress }, Modifier.fillMaxWidth())
                        else LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                    is State.Installing -> Text(stringResource(R.string.update_installing), Modifier.padding(top = 16.dp))
                    is State.Failed -> Text(s.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 16.dp))
                    else -> if (!canInstall) {
                        Text(
                            stringResource(R.string.update_permission_needed),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && (state !is State.Installing || waitingForSystem), onClick = {
                if (waitingForSystem) {
                    updates.confirm()
                } else if (!canInstall) {
                    context.startActivity(
                        Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    )
                } else {
                    // App scope: the download goes on if the screen is recreated.
                    container.appScope.launch { updates.install(info) }
                }
            }) {
                Text(
                    stringResource(
                        when {
                            waitingForSystem -> R.string.update_continue
                            !canInstall -> R.string.update_allow
                            state is State.Failed -> R.string.retry
                            else -> R.string.update_install
                        }
                    )
                )
            }
        },
        dismissButton = { if (!busy) TextButton(onClick = dismiss) { Text(stringResource(R.string.update_later)) } },
    )
}
