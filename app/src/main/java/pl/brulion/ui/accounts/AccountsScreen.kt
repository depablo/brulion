package pl.brulion.ui.accounts

import android.text.format.DateUtils
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pl.brulion.data.sync.SectionResult
import pl.brulion.data.sync.SyncController
import pl.brulion.data.sync.SyncReport
import pl.brulion.data.sync.SyncSection
import pl.brulion.ui.common.toUiError
import pl.brulion.R
import pl.brulion.data.db.ProfileEntity
import pl.brulion.ui.common.NameAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(
    viewModel: AccountsViewModel,
    onBack: () -> Unit,
    onAddAccount: () -> Unit,
    onNoProfiles: () -> Unit,
) {
    val profiles by viewModel.profiles.collectAsStateWithLifecycle()
    val syncs by viewModel.syncs.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var toRemove by remember { mutableStateOf<ProfileEntity?>(null) }

    LaunchedEffect(profiles) {
        if (profiles?.isEmpty() == true) onNoProfiles()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.accounts_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = onAddAccount) {
                        Icon(Icons.Default.PersonAdd, stringResource(R.string.home_add_account))
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        if (profiles == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(profiles.orEmpty(), key = { it.id }) { profile ->
                ProfileCard(
                    profile = profile,
                    sync = syncs[profile.id],
                    onSync = { viewModel.sync(profile.id) },
                    onRemove = { toRemove = profile },
                )
            }
        }
    }

    toRemove?.let { profile ->
        AlertDialog(
            onDismissRequest = { toRemove = null },
            title = { Text(stringResource(R.string.home_remove_title, profile.studentName)) },
            text = { Text(stringResource(R.string.home_remove_text)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.remove(profile.id)
                    toRemove = null
                }) { Text(stringResource(R.string.home_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { toRemove = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun ProfileCard(
    profile: ProfileEntity,
    sync: SyncController.State?,
    onSync: () -> Unit,
    onRemove: () -> Unit,
) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NameAvatar(profile.studentName)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = profile.studentName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(
                            if (profile.isParent) R.string.home_parent_account else R.string.home_student_account,
                            profile.synergiaLogin,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ProfileMenu(onRemove)
            }
            Spacer(Modifier.size(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(onClick = onSync, enabled = sync != SyncController.State.Running) {
                    Text(stringResource(R.string.home_sync))
                }
                Spacer(Modifier.width(12.dp))
                if (sync == SyncController.State.Running) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        text = lastSyncText(profile.lastSyncAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            when (sync) {
                is SyncController.State.Done -> SyncReportView(sync.report)
                is SyncController.State.Failed -> {
                    val error = sync.error.toUiError()
                    ErrorLine(stringResource(error.message), error.detail)
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun lastSyncText(lastSyncAt: Long): String {
    if (lastSyncAt == 0L) return stringResource(R.string.home_sync_never)
    val time = DateUtils.getRelativeTimeSpanString(lastSyncAt * 1000).toString()
    return stringResource(R.string.home_sync_last, time)
}

@Composable
private fun SyncReportView(report: SyncReport) {
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for ((section, result) in report.results) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(section.label),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                when (result) {
                    is SectionResult.Ok -> Text(
                        text = if (result.new > 0) {
                            stringResource(R.string.sync_total_new, result.total, result.new)
                        } else {
                            stringResource(R.string.sync_total, result.total)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (result.new > 0) FontWeight.SemiBold else FontWeight.Normal,
                    )
                    is SectionResult.Skipped -> Text(
                        text = stringResource(R.string.sync_skipped),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    is SectionResult.Failed -> {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            if (result is SectionResult.Failed) {
                val error = result.error.toUiError()
                ErrorLine(stringResource(error.message), error.detail)
            }
        }
    }
}

@Composable
private fun ErrorLine(message: String, detail: String?) {
    Text(
        text = message + (detail?.let { " ($it)" } ?: ""),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

private val SyncSection.label: Int
    get() = when (this) {
        SyncSection.GRADES -> R.string.section_grades
        SyncSection.ANNOUNCEMENTS -> R.string.section_announcements
        SyncSection.ATTENDANCE -> R.string.section_attendance
        SyncSection.EVENTS -> R.string.section_events
        SyncSection.NOTES -> R.string.section_notes
        SyncSection.LUCKY_NUMBER -> R.string.section_lucky_number
        SyncSection.TIMETABLE -> R.string.section_timetable
        SyncSection.MESSAGES -> R.string.section_messages
    }

@Composable
private fun ProfileMenu(onRemove: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, stringResource(R.string.home_more))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.home_remove)) },
                onClick = {
                    expanded = false
                    onRemove()
                },
            )
        }
    }
}
