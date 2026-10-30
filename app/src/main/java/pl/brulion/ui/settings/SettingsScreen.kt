package pl.brulion.ui.settings

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.NotificationsPaused
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Summarize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.ExistingWorkPolicy
import kotlinx.coroutines.launch
import pl.brulion.R
import pl.brulion.data.Diagnostics
import pl.brulion.data.calendar.CalendarExport
import pl.brulion.data.db.FeedKind
import pl.brulion.data.summary.DailySummaryWorker
import pl.brulion.ui.common.appContainer
import pl.brulion.ui.update.UpdateSettings

private const val SOURCE_URL = "https://github.com/depablo/brulion"
private const val SZKOLNY_URL = "https://github.com/szkolny-eu/szkolny-android"

private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        // No browser; nothing sensible to do.
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = appContainer()
    val settings = container.settings
    val enabled by settings.summaryEnabled.collectAsStateWithLifecycle()
    val time by settings.summaryTime.collectAsStateWithLifecycle()
    val whenQuiet by settings.summaryWhenQuiet.collectAsStateWithLifecycle()
    var canNotify by remember { mutableStateOf(container.dailySummary.canNotify()) }
    var pickTime by remember { mutableStateOf(false) }
    var sent by remember { mutableStateOf(false) }

    // The user may come back from system settings with notifications allowed.
    LifecycleResumeEffect(Unit) {
        canNotify = container.dailySummary.canNotify()
        onPauseOrDispose {}
    }

    fun reschedule() = DailySummaryWorker.schedule(context, settings, ExistingWorkPolicy.REPLACE)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            Text(
                text = stringResource(R.string.settings_summary_section),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_summary)) },
                supportingContent = { Text(stringResource(R.string.settings_summary_description)) },
                leadingContent = { Icon(Icons.Outlined.Summarize, null) },
                trailingContent = {
                    Switch(checked = enabled, onCheckedChange = {
                        settings.setSummaryEnabled(it)
                        reschedule()
                    })
                },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_summary_time)) },
                supportingContent = { Text("%02d:%02d".format(time / 60, time % 60)) },
                leadingContent = { Icon(Icons.Outlined.Schedule, null) },
                modifier = Modifier.clickable(enabled = enabled) { pickTime = true },
            )
            if (!canNotify) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_notifications_off)) },
                    supportingContent = { Text(stringResource(R.string.settings_notifications_off_description)) },
                    leadingContent = { Icon(Icons.Outlined.NotificationsOff, null, tint = MaterialTheme.colorScheme.error) },
                    modifier = Modifier.clickable {
                        context.startActivity(
                            Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
                        )
                    },
                )
            }
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_summary_quiet)) },
                supportingContent = { Text(stringResource(R.string.settings_summary_quiet_description)) },
                leadingContent = { Icon(Icons.Outlined.NotificationsPaused, null) },
                trailingContent = {
                    Switch(checked = whenQuiet, enabled = enabled, onCheckedChange = settings::setSummaryWhenQuiet)
                },
            )
            InstantNotificationsSetting(enabled = canNotify)
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = {
                        DailySummaryWorker.runNow(context)
                        sent = true
                    },
                    enabled = canNotify,
                ) { Text(stringResource(R.string.settings_summary_now)) }
                Text(
                    text = stringResource(if (sent) R.string.settings_summary_now_sent else R.string.settings_summary_now_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(R.string.settings_calendar_section),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
            )
            CalendarExportSetting()
            Text(
                text = stringResource(R.string.settings_updates_section),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
            )
            UpdateSettings()
            Text(
                text = stringResource(R.string.settings_help_section),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_send_diagnostics)) },
                supportingContent = { Text(stringResource(R.string.settings_send_diagnostics_description, Diagnostics.EMAIL)) },
                leadingContent = { Icon(Icons.Outlined.BugReport, null) },
                modifier = Modifier.clickable { container.diagnostics.send("Zgłoszenie z ustawień") },
            )
            Text(
                text = stringResource(R.string.settings_about_section),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
            )
            // GPL v3 asks for the license, the lack of warranty and the source to be visible in the app.
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_about_source)) },
                supportingContent = { Text(stringResource(R.string.settings_about_source_description)) },
                leadingContent = { Icon(Icons.Outlined.Code, null) },
                modifier = Modifier.clickable { openUrl(context, SOURCE_URL) },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_about_szkolny)) },
                supportingContent = { Text(stringResource(R.string.settings_about_szkolny_description)) },
                leadingContent = { Icon(Icons.Outlined.Favorite, null) },
                modifier = Modifier.clickable { openUrl(context, SZKOLNY_URL) },
            )
            Text(
                text = stringResource(R.string.settings_about_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }

    if (pickTime) {
        val state = rememberTimePickerState(initialHour = time / 60, initialMinute = time % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            title = { Text(stringResource(R.string.settings_summary_time)) },
            text = { TimePicker(state) },
            confirmButton = {
                TextButton(onClick = {
                    settings.setSummaryTime(state.hour * 60 + state.minute)
                    reschedule()
                    pickTime = false
                }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Planner entries in a phone calendar the user picks; asks for the calendar permission first. */
@Composable
private fun CalendarExportSetting() {
    val container = appContainer()
    val export = container.calendarExport
    val calendarId by container.settings.exportCalendarId.collectAsStateWithLifecycle()
    var calendars by remember { mutableStateOf<List<CalendarExport.Calendar>?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) calendars = export.calendars()
    }
    val chosen = remember(calendarId) { calendarId?.let { id -> export.calendars().firstOrNull { it.id == id } } }

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_calendar)) },
        supportingContent = {
            Text(
                when {
                    calendarId == null -> stringResource(R.string.settings_calendar_off)
                    chosen != null -> stringResource(R.string.settings_calendar_on, chosen.name)
                    else -> stringResource(R.string.settings_calendar_missing)
                }
            )
        },
        leadingContent = { Icon(Icons.Outlined.CalendarMonth, null) },
        modifier = Modifier.clickable {
            if (export.hasPermission()) calendars = export.calendars() else permission.launch(CalendarExport.PERMISSIONS)
        },
    )

    calendars?.let { list ->
        AlertDialog(
            onDismissRequest = { calendars = null },
            title = { Text(stringResource(R.string.settings_calendar_pick)) },
            text = {
                Column {
                    Text(stringResource(R.string.settings_calendar_pick_hint), style = MaterialTheme.typography.bodyMedium)
                    if (list.isEmpty()) {
                        Text(stringResource(R.string.settings_calendar_none), Modifier.padding(top = 8.dp))
                    }
                    list.forEach { calendar ->
                        ListItem(
                            headlineContent = { Text(calendar.name) },
                            supportingContent = { Text(calendar.account) },
                            leadingContent = { RadioButton(selected = calendar.id == calendarId, onClick = null) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable {
                                calendars = null
                                container.appScope.launch { export.use(calendar.id) }
                            },
                        )
                    }
                }
            },
            confirmButton = {
                if (calendarId != null) {
                    TextButton(onClick = {
                        calendars = null
                        container.appScope.launch { export.disable() }
                    }) { Text(stringResource(R.string.settings_calendar_disable)) }
                }
            },
            dismissButton = { TextButton(onClick = { calendars = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/** Kinds of news notified right after the sync that brought them, besides the evening summary. */
@Composable
private fun InstantNotificationsSetting(enabled: Boolean) {
    val container = appContainer()
    val kinds by container.settings.instantKinds.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    val labels = instantKindLabels()

    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_instant)) },
        supportingContent = {
            Text(
                if (kinds.isEmpty()) stringResource(R.string.settings_instant_off)
                else labels.filterKeys { it in kinds }.values.joinToString(", ")
            )
        },
        leadingContent = { Icon(Icons.Outlined.NotificationsActive, null) },
        modifier = Modifier.clickable(enabled = enabled) { editing = true },
    )

    if (editing) {
        var picked by remember { mutableStateOf(kinds.ifEmpty { DEFAULT_INSTANT_KINDS }) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(R.string.settings_instant)) },
            text = {
                Column {
                    Text(stringResource(R.string.settings_instant_hint), style = MaterialTheme.typography.bodyMedium)
                    labels.forEach { (kind, label) ->
                        ListItem(
                            headlineContent = { Text(label) },
                            leadingContent = { Checkbox(checked = kind in picked, onCheckedChange = null) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable { picked = if (kind in picked) picked - kind else picked + kind },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    editing = false
                    container.appScope.launch { container.instantNotifier.enable(picked) }
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    editing = false
                    container.appScope.launch { container.instantNotifier.enable(emptySet()) }
                }) { Text(stringResource(R.string.settings_instant_disable)) }
            },
        )
    }
}

private val DEFAULT_INSTANT_KINDS = setOf(FeedKind.GRADE, FeedKind.NOTE, FeedKind.LESSON_CHANGE, FeedKind.MESSAGE)

@Composable
private fun instantKindLabels(): Map<FeedKind, String> = linkedMapOf(
    FeedKind.GRADE to stringResource(R.string.instant_kind_grade),
    FeedKind.NOTE to stringResource(R.string.instant_kind_note),
    FeedKind.LESSON_CHANGE to stringResource(R.string.instant_kind_lesson_change),
    FeedKind.MESSAGE to stringResource(R.string.instant_kind_message),
    FeedKind.ATTENDANCE to stringResource(R.string.instant_kind_attendance),
    FeedKind.EVENT to stringResource(R.string.instant_kind_event),
    FeedKind.ANNOUNCEMENT to stringResource(R.string.instant_kind_announcement),
)
