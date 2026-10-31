package pl.brulion.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.brulion.AppContainer
import pl.brulion.data.db.ProfileEntity
import pl.brulion.data.sync.SyncController

class MainViewModel(private val container: AppContainer) : ViewModel() {
    val current: StateFlow<ProfileEntity?> = container.currentProfile.profile
    val profiles: StateFlow<List<ProfileEntity>> =
        container.accounts.allProfiles.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val syncStates: StateFlow<Map<Long, SyncController.State>> = container.sync.states
    val openFeed: StateFlow<Boolean> = container.openFeedRequest

    /** Coming back to the app refreshes the shown student if the data is getting old. */
    fun onResume() {
        viewModelScope.launch {
            container.sync.syncIfStale(current.filterNotNull().first())
        }
    }

    fun select(profile: ProfileEntity) {
        container.currentProfile.select(profile.id)
        container.sync.syncIfStale(profile)
    }

    fun feedOpened() {
        container.openFeedRequest.value = false
    }

    fun refresh() {
        current.value?.let { container.sync.sync(it.id) }
    }

    /**
     * Clears Brulion's "new" markers and unread messages for every student; Librus isn't told.
     * Returns an undo.
     */
    suspend fun markAllRead(): suspend () -> Unit {
        val dao = container.database.profileDao()
        val before = container.accounts.allProfiles.first().associate { it.id to it.markedReadAt }
        dao.markAllRead(System.currentTimeMillis() / 1000)
        return { before.forEach { (id, time) -> dao.setMarkedReadAt(id, time) } }
    }
}
