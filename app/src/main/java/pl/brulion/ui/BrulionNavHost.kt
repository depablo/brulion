package pl.brulion.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import kotlinx.serialization.Serializable
import pl.brulion.ui.accounts.AccountsScreen
import pl.brulion.ui.accounts.AccountsViewModel
import pl.brulion.ui.common.LocalMarkedReadAt
import pl.brulion.ui.common.appContainer
import pl.brulion.ui.common.appViewModel
import pl.brulion.ui.login.LoginViewModel
import pl.brulion.ui.login.PortalLoginScreen
import pl.brulion.ui.login.WelcomeScreen
import pl.brulion.ui.main.MainScreen
import pl.brulion.ui.main.MainViewModel
import pl.brulion.ui.main.MoreDestination
import pl.brulion.ui.main.MoreScreen
import pl.brulion.ui.messages.ComposeScreen
import pl.brulion.ui.messages.ComposeViewModel
import pl.brulion.ui.messages.MessageScreen
import pl.brulion.ui.messages.MessageViewModel
import pl.brulion.ui.messages.ReplyScreen
import pl.brulion.ui.messages.ReplyViewModel
import pl.brulion.ui.more.AnnouncementsScreen
import pl.brulion.ui.more.AttendanceScreen
import pl.brulion.ui.more.EventsScreen
import pl.brulion.ui.more.NotesScreen
import pl.brulion.ui.settings.SettingsScreen

@Serializable object WelcomeRoute
@Serializable object PortalLoginRoute
@Serializable object MainRoute
@Serializable object AccountsRoute
@Serializable object AnnouncementsRoute
@Serializable object EventsRoute
@Serializable object AttendanceRoute
@Serializable object NotesRoute
@Serializable object SettingsRoute
@Serializable object MoreRoute
@Serializable data class MessageRoute(val profileId: Long, val messageId: Long)
@Serializable data class ReplyRoute(val profileId: Long, val messageId: Long)
@Serializable data class ComposeRoute(val profileId: Long)

@Composable
fun BrulionNavHost(startDestination: Any, navController: NavHostController = rememberNavController()) {
    val goMain = {
        navController.navigate(MainRoute) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
    }
    val back: () -> Unit = { navController.popBackStack() }
    val openDestination: (MoreDestination) -> Unit = { destination ->
        navController.navigate(
            when (destination) {
                MoreDestination.MORE -> MoreRoute
                MoreDestination.ANNOUNCEMENTS -> AnnouncementsRoute
                MoreDestination.EVENTS -> EventsRoute
                MoreDestination.ATTENDANCE -> AttendanceRoute
                MoreDestination.NOTES -> NotesRoute
                MoreDestination.ACCOUNTS -> AccountsRoute
                MoreDestination.SETTINGS -> SettingsRoute
            }
        )
    }

    val profile by appContainer().currentProfile.profile.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalMarkedReadAt provides (profile?.markedReadAt ?: 0L)) {
        NavHost(navController = navController, startDestination = startDestination) {
            composable<WelcomeRoute> {
                WelcomeScreen(
                    onPortalLogin = { navController.navigate(PortalLoginRoute) },
                    onBack = if (navController.previousBackStackEntry != null) back else null,
                )
            }
            composable<PortalLoginRoute> {
                PortalLoginScreen(
                    viewModel = appViewModel { LoginViewModel(it.accounts) },
                    onBack = back,
                    onLoggedIn = goMain,
                )
            }
            composable<MainRoute> {
                MainScreen(
                    viewModel = appViewModel { MainViewModel(it) },
                    onOpen = openDestination,
                    onOpenMessage = { profileId, messageId -> navController.navigate(MessageRoute(profileId, messageId)) },
                    onCompose = { navController.navigate(ComposeRoute(it)) },
                )
            }
            composable<MessageRoute> { entry ->
                val route = entry.toRoute<MessageRoute>()
                MessageScreen(
                    viewModel = appViewModel { MessageViewModel(it.database, it.messages, it.downloads, route.profileId, route.messageId) },
                    onBack = back,
                    onReply = { navController.navigate(ReplyRoute(route.profileId, route.messageId)) },
                )
            }
            composable<ReplyRoute> { entry ->
                val route = entry.toRoute<ReplyRoute>()
                ReplyScreen(
                    viewModel = appViewModel { ReplyViewModel(it.messages, it.appScope, route.profileId, route.messageId) },
                    profileId = route.profileId,
                    messageId = route.messageId,
                    onClose = back,
                )
            }
            composable<ComposeRoute> { entry ->
                val profileId = entry.toRoute<ComposeRoute>().profileId
                ComposeScreen(viewModel = appViewModel { ComposeViewModel(it.messages, it.appScope, profileId) }, onClose = back)
            }
            composable<AccountsRoute> {
                AccountsScreen(
                    viewModel = appViewModel { AccountsViewModel(it.accounts, it.sync) },
                    onBack = back,
                    onAddAccount = { navController.navigate(WelcomeRoute) },
                    onNoProfiles = {
                        navController.navigate(WelcomeRoute) {
                            popUpTo(navController.graph.id) { inclusive = true }
                        }
                    },
                )
            }
            composable<AnnouncementsRoute> { AnnouncementsScreen(back) }
            composable<EventsRoute> { EventsScreen(back) }
            composable<AttendanceRoute> { AttendanceScreen(back) }
            composable<NotesRoute> { NotesScreen(back) }
            composable<SettingsRoute> { SettingsScreen(back) }
            composable<MoreRoute> { MoreScreen(onBack = back, onOpen = openDestination) }
        }
    }
}
