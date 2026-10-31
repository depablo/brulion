package pl.brulion

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import pl.brulion.data.AccountRepository
import pl.brulion.data.Announcements
import pl.brulion.data.CurrentProfile
import pl.brulion.data.Diagnostics
import pl.brulion.data.Downloads
import pl.brulion.data.MessageSessions
import pl.brulion.data.MessagesRepository
import pl.brulion.data.calendar.CalendarExport
import pl.brulion.data.feed.AbsenceNotifier
import pl.brulion.data.feed.InstantNotifier
import pl.brulion.data.migrateChildKeys
import pl.brulion.data.Settings
import pl.brulion.data.db.BrulionDatabase
import pl.brulion.data.feed.FeedRecorder
import pl.brulion.data.summary.DailySummaryService
import pl.brulion.data.summary.SummaryNotifier
import pl.brulion.data.summary.SummaryRepository
import pl.brulion.data.sync.SyncController
import pl.brulion.data.sync.SyncRepository
import pl.brulion.data.update.UpdateManager
import pl.brulion.librus.LibrusApi
import pl.brulion.librus.LibrusHttp
import pl.brulion.librus.LibrusMessages
import pl.brulion.librus.PortalAuth
import pl.brulion.widget.TimetableWidget

/** Manual dependency container; the app is small enough not to need a DI framework. */
class AppContainer(context: Context) {
    /** Work that should outlive a single screen, like a sync started before leaving it. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val diagnostics = Diagnostics(context)
    private val http = LibrusHttp(debug = BuildConfig.DEBUG, onRequest = diagnostics::log)
    val database = BrulionDatabase.create(context)
    val librusApi = LibrusApi(http)
    val accounts = AccountRepository(database, PortalAuth(http), librusApi)
    private val messageSessions = MessageSessions(accounts, librusApi, LibrusMessages(http))
    val settings = Settings(context)
    val announcements = Announcements(database, settings)
    private val absenceNotifier = AbsenceNotifier(context)
    private val feedRecorder = FeedRecorder(database, announcements, onAbsenceReminder = absenceNotifier::show)
    val calendarExport = CalendarExport(context, database, accounts, settings, diagnostics)
    val instantNotifier = InstantNotifier(context, database, accounts, announcements, settings)
    val sync = SyncController(
        SyncRepository(database, accounts, librusApi, messageSessions, feedRecorder),
        appScope,
        diagnostics,
        onSynced = {
            TimetableWidget.updateAll(context)
            appScope.launch { calendarExport.sync() }
            appScope.launch { instantNotifier.check() }
        },
    )
    val messages = MessagesRepository(database, messageSessions, context.filesDir, diagnostics)
    val currentProfile = CurrentProfile(context, accounts, appScope) { TimetableWidget.updateAll(context) }
    /** Set when a summary notification is tapped; the main screen then shows the feed. */
    val openFeedRequest = MutableStateFlow(false)
    val downloads = Downloads(context)
    val updates = UpdateManager(context, diagnostics)
    val dailySummary = DailySummaryService(
        accounts = accounts,
        sync = sync,
        repository = SummaryRepository(database, announcements),
        notifier = SummaryNotifier(context),
        settings = settings,
    )

    init {
        // Once after an update that changed how the feed is kept: move old data over and fill the
        // feed from what's already on the phone. Later, every sync records its own profile.
        if (settings.feedRevision < FEED_REVISION) {
            appScope.launch(Dispatchers.Default) {
                val profiles = accounts.allProfiles.first()
                migrateChildKeys(database, settings, profiles)
                profiles.forEach { feedRecorder.record(it) }
                settings.feedRevision = FEED_REVISION
            }
        }
    }

    private companion object {
        /** Raise when the feed needs rebuilding from local data after an update. */
        const val FEED_REVISION = 1
    }
}
