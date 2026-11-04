package pl.brulion.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.first
import pl.brulion.BrulionApp
import pl.brulion.ui.theme.BrulionTheme
import pl.brulion.ui.update.UpdatePrompt

class MainActivity : ComponentActivity() {
    private val container get() = (application as BrulionApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        val accounts = container.accounts

        setContent {
            BrulionTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    var startDestination by remember { mutableStateOf<Any?>(null) }
                    LaunchedEffect(Unit) {
                        val hasProfiles = accounts.allProfiles.first().isNotEmpty()
                        startDestination = if (hasProfiles) MainRoute else WelcomeRoute
                    }
                    startDestination?.let { BrulionNavHost(it) }
                    UpdatePrompt()
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** A tapped summary notification opens the child it is about; an update one shows the update. */
    private fun handleIntent(intent: Intent?) {
        val profileId = intent?.getLongExtra(EXTRA_PROFILE_ID, 0) ?: 0
        if (profileId != 0L) container.currentProfile.select(profileId)
        if (intent?.getBooleanExtra(EXTRA_OPEN_FEED, false) == true) container.openFeedRequest.value = true
        if (intent?.getBooleanExtra(EXTRA_SHOW_UPDATE, false) == true) container.updates.promptRequest.value = true
    }

    companion object {
        const val EXTRA_PROFILE_ID = "profile_id"
        const val EXTRA_OPEN_FEED = "open_feed"
        const val EXTRA_SHOW_UPDATE = "show_update"

        /**
         * Intent for a notification or widget tap. PendingIntents treat intents without action or
         * data as equal, and then ignore extras, so two sources with the same request code would
         * overwrite each other. A unique [source] and [id] in the data URI keeps them apart.
         */
        fun openIntent(context: Context, source: String, id: String): Intent =
            Intent(context, MainActivity::class.java)
                .setData(Uri.Builder().scheme("brulion").authority(source).appendPath(id).build())
    }
}
