package pl.brulion.ui.main

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import pl.brulion.R

private data class MoreEntry(val destination: MoreDestination, val label: Int, val icon: ImageVector)

private val Entries = listOf(
    MoreEntry(MoreDestination.ANNOUNCEMENTS, R.string.section_announcements, Icons.Outlined.Campaign),
    MoreEntry(MoreDestination.EVENTS, R.string.section_events, Icons.Outlined.Event),
    MoreEntry(MoreDestination.ATTENDANCE, R.string.section_attendance, Icons.Outlined.FactCheck),
    MoreEntry(MoreDestination.NOTES, R.string.section_notes, Icons.Outlined.StickyNote2),
    MoreEntry(MoreDestination.ACCOUNTS, R.string.accounts_title, Icons.Outlined.ManageAccounts),
    MoreEntry(MoreDestination.SETTINGS, R.string.settings_title, Icons.Outlined.Settings),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(onBack: () -> Unit, onOpen: (MoreDestination) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_more)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding -> MoreList(Modifier.padding(padding), onOpen) }
}

@Composable
private fun MoreList(modifier: Modifier, onOpen: (MoreDestination) -> Unit) {
    LazyColumn(modifier.fillMaxSize()) {
        items(Entries) { entry ->
            if (entry.destination == MoreDestination.ACCOUNTS) HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(entry.label)) },
                leadingContent = { Icon(entry.icon, contentDescription = null) },
                modifier = Modifier.clickable { onOpen(entry.destination) },
            )
        }
    }
}
