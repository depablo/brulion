package pl.brulion.ui.main

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import pl.brulion.R
import pl.brulion.data.db.FeedKind
import pl.brulion.data.db.ProfileEntity
import pl.brulion.data.sync.SectionResult
import pl.brulion.data.sync.SyncController
import pl.brulion.ui.common.CrashReportDialog
import pl.brulion.ui.common.NameAvatar
import pl.brulion.ui.common.appContainer
import pl.brulion.ui.common.toUiError
import pl.brulion.ui.feed.FeedTab
import pl.brulion.ui.grades.GradesTab
import pl.brulion.ui.messages.MessagesTab
import pl.brulion.ui.timetable.TimetableTab
import pl.brulion.ui.today.TodayTab

enum class MainTab(@StringRes val label: Int, val icon: ImageVector) {
    TODAY(R.string.tab_today, Icons.Outlined.WbSunny),
    FEED(R.string.tab_feed, Icons.Outlined.DynamicFeed),
    GRADES(R.string.section_grades, Icons.Outlined.School),
    TIMETABLE(R.string.tab_timetable, Icons.Outlined.CalendarViewWeek),
    MESSAGES(R.string.tab_messages, Icons.Outlined.Mail),
}

/** Secondary screens reachable from the main screen. */
enum class MoreDestination { MORE, ANNOUNCEMENTS, EVENTS, ATTENDANCE, NOTES, ACCOUNTS, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel, onOpen: (MoreDestination) -> Unit, onOpenMessage: (profileId: Long, messageId: Long) -> Unit, onCompose: (profileId: Long) -> Unit) {
    val profile by viewModel.current.collectAsStateWithLifecycle()
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val syncStates by viewModel.syncStates.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(MainTab.TODAY) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    val current = profile
    val syncState = current?.let { syncStates[it.id] }

    SyncSnackbar(syncState, snackbar)
    CrashReportDialog()
    AskForNotifications()
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose {}
    }

    // A tapped summary notification asks for the feed.
    val openFeed by viewModel.openFeed.collectAsStateWithLifecycle()
    LaunchedEffect(openFeed) {
        if (openFeed) {
            tab = MainTab.FEED
            viewModel.feedOpened()
        }
    }

    val db = appContainer().database
    val unread by remember(current?.id) {
        current?.let { db.messageDao().observeUnreadCount(it.id) } ?: flowOf(0)
    }.collectAsStateWithLifecycle(0)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (current != null) ProfileSwitcher(current, profiles, viewModel::select) { onOpen(MoreDestination.ACCOUNTS) }
                },
                actions = {
                    IconButton(onClick = {
                        scope.launch {
                            val undo = viewModel.markAllRead()
                            val result = snackbar.showSnackbar(
                                resources.getString(R.string.mark_all_read_done),
                                actionLabel = resources.getString(R.string.undo),
                                withDismissAction = true,
                            )
                            if (result == SnackbarResult.ActionPerformed) undo()
                        }
                    }) {
                        Icon(Icons.Outlined.DoneAll, stringResource(R.string.mark_all_read))
                    }
                    IconButton(onClick = { onOpen(MoreDestination.MORE) }) {
                        Icon(Icons.Outlined.Menu, stringResource(R.string.tab_more))
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                for (item in MainTab.entries) {
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = {
                            BadgedBox(badge = { if (item == MainTab.MESSAGES && unread > 0) Badge { Text(unread.toString()) } }) {
                                Icon(item.icon, contentDescription = null)
                            }
                        },
                        label = { Text(stringResource(item.label), maxLines = 1, softWrap = false) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (current == null) return@Scaffold
        PullToRefreshBox(
            isRefreshing = syncState == SyncController.State.Running,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when (tab) {
                MainTab.TODAY -> TodayTab(
                    profile = current,
                    onShowTimetable = { tab = MainTab.TIMETABLE },
                    onShowGrades = { tab = MainTab.GRADES },
                    onShowMessages = { tab = MainTab.MESSAGES },
                    onOpenMessage = { onOpenMessage(current.id, it) },
                    onShowEvents = { onOpen(MoreDestination.EVENTS) },
                    onShowAnnouncements = { onOpen(MoreDestination.ANNOUNCEMENTS) },
                )
                MainTab.GRADES -> GradesTab(current.id)
                MainTab.TIMETABLE -> TimetableTab(current.id)
                MainTab.MESSAGES -> MessagesTab(current.id, onOpen = { onOpenMessage(current.id, it) }, onCompose = { onCompose(current.id) })
                MainTab.FEED -> FeedTab(profiles) { entry ->
                    profiles.find { it.id == entry.profileId }?.let(viewModel::select)
                    when (entry.kind) {
                        FeedKind.MESSAGE -> entry.targetId?.let { onOpenMessage(entry.profileId, it) }
                        FeedKind.GRADE -> tab = MainTab.GRADES
                        FeedKind.LESSON_CHANGE -> tab = MainTab.TIMETABLE
                        FeedKind.EVENT -> onOpen(MoreDestination.EVENTS)
                        FeedKind.ANNOUNCEMENT -> onOpen(MoreDestination.ANNOUNCEMENTS)
                        FeedKind.NOTE -> onOpen(MoreDestination.NOTES)
                        FeedKind.ATTENDANCE, FeedKind.ABSENCE_REMINDER -> onOpen(MoreDestination.ATTENDANCE)
                    }
                }
            }
        }
    }
}

/** Reports sync problems once per finished sync, with an option to send diagnostics. */
@Composable
private fun SyncSnackbar(state: SyncController.State?, snackbar: SnackbarHostState) {
    val resources = LocalResources.current
    val diagnostics = appContainer().diagnostics
    LaunchedEffect(state) {
        val message = when (state) {
            is SyncController.State.Failed -> resources.getString(state.error.toUiError().message)
            is SyncController.State.Done -> {
                val failed = state.report.results.values.filterIsInstance<SectionResult.Failed>()
                if (failed.isEmpty()) null else resources.getString(R.string.sync_partial, failed.size)
            }
            else -> null
        }
        if (message != null) {
            val result = snackbar.showSnackbar(message, actionLabel = resources.getString(R.string.report), withDismissAction = true)
            if (result == SnackbarResult.ActionPerformed) diagnostics.send("Błąd synchronizacji: $message")
        }
    }
}

@Composable
private fun ProfileSwitcher(
    current: ProfileEntity,
    profiles: List<ProfileEntity>,
    onSelect: (ProfileEntity) -> Unit,
    onManage: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            NameAvatar(current.studentName, size = 32.dp)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = current.studentName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(if (current.isParent) R.string.profile_parent else R.string.profile_student),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.profile_switch))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            for (profile in profiles) {
                DropdownMenuItem(
                    leadingIcon = { NameAvatar(profile.studentName, size = 28.dp) },
                    text = {
                        Column {
                            Text(profile.studentName)
                            Text(
                                text = stringResource(if (profile.isParent) R.string.profile_parent else R.string.profile_student),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    trailingIcon = { if (profile.id == current.id) Icon(Icons.Default.Check, null) },
                    onClick = {
                        expanded = false
                        onSelect(profile)
                    },
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Outlined.ManageAccounts, null) },
                text = { Text(stringResource(R.string.accounts_title)) },
                onClick = {
                    expanded = false
                    onManage()
                },
            )
        }
    }
}

/**
 * The daily summary is the point of the app, so ask as soon as there's someone to summarize,
 * also right after the first login. Once per screen; Android stops asking after two refusals.
 */
@Composable
private fun AskForNotifications() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val summary = appContainer().dailySummary
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    var asked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!asked && !summary.canNotify()) {
            asked = true
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
