package pl.brulion.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import pl.brulion.data.db.ProfileEntity

/** Which student the main screens show; remembered across launches. */
class CurrentProfile(
    context: Context,
    accounts: AccountRepository,
    scope: CoroutineScope,
    private val onChange: () -> Unit = {},
) {
    private val profiles = accounts.allProfiles
    private val prefs = context.getSharedPreferences("brulion", Context.MODE_PRIVATE)
    private val selectedId = MutableStateFlow(prefs.getLong(KEY, 0))

    /** Null while loading and when there are no profiles; falls back to the first one. */
    val profile: StateFlow<ProfileEntity?> = combine(profiles, selectedId) { profiles, id ->
        profiles.find { it.id == id } ?: profiles.firstOrNull()
    }.stateIn(scope, SharingStarted.Eagerly, null)

    /** The shown profile, waiting for the database if needed (for widgets and workers). */
    suspend fun resolve(): ProfileEntity? = profiles.first().let { all -> all.find { it.id == selectedId.value } ?: all.firstOrNull() }

    fun select(profileId: Long) {
        prefs.edit().putLong(KEY, profileId).apply()
        selectedId.value = profileId
        onChange()
    }

    private companion object {
        const val KEY = "selected_profile"
    }
}
