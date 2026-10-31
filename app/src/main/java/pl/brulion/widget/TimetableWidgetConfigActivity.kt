package pl.brulion.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.SyncAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pl.brulion.BrulionApp
import pl.brulion.R
import pl.brulion.data.childKey
import pl.brulion.data.db.ProfileEntity
import pl.brulion.ui.common.NameAvatar
import pl.brulion.ui.theme.BrulionTheme

/** Picks the student a timetable widget shows; opened when the widget is placed or reconfigured. */
class TimetableWidgetConfigActivity : ComponentActivity() {

    private val appWidgetId by lazy {
        intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Backing out without a choice cancels placing the widget.
        setResult(RESULT_CANCELED)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        enableEdgeToEdge()
        val container = (application as BrulionApp).container
        lifecycleScope.launch {
            val current = TimetableWidget.profileOf(this@TimetableWidgetConfigActivity, appWidgetId)
            val profiles = onePerChild(container.accounts.allProfiles.first(), current)
            // Nothing to choose from: follow the app.
            if (profiles.size <= 1) {
                choose(null)
                return@launch
            }
            setContent {
                BrulionTheme {
                    ProfilePicker(profiles, current, ::choose)
                }
            }
        }
    }

    private fun choose(profileId: Long?) {
        TimetableWidget.setProfile(this, appWidgetId, profileId)
        TimetableWidget.update(this, appWidgetId)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }
}

/**
 * A child seen through a parent and a student account has the same timetable, so it's listed
 * once; the widget's current profile wins, so its check mark stays.
 */
internal fun onePerChild(profiles: List<ProfileEntity>, current: Long?): List<ProfileEntity> =
    profiles.groupBy { childKey(it) }.values.map { same -> same.find { it.id == current } ?: same.first() }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfilePicker(profiles: List<ProfileEntity>, current: Long?, onChoose: (Long?) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.widget_config_title)) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.widget_config_follow_app)) },
                supportingContent = { Text(stringResource(R.string.widget_config_follow_app_description)) },
                leadingContent = { Icon(Icons.Outlined.SyncAlt, null, Modifier.size(32.dp)) },
                trailingContent = { if (current == null) Icon(Icons.Default.Check, null) },
                modifier = Modifier.clickable { onChoose(null) },
            )
            for (profile in profiles) {
                ListItem(
                    headlineContent = { Text(profile.studentName) },
                    leadingContent = { NameAvatar(profile.studentName, size = 32.dp) },
                    trailingContent = { if (current == profile.id) Icon(Icons.Default.Check, null) },
                    modifier = Modifier.clickable { onChoose(profile.id) },
                )
            }
        }
    }
}
