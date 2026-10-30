package pl.brulion.ui.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.brulion.data.AccountRepository
import pl.brulion.data.db.ProfileEntity
import pl.brulion.data.sync.SyncController

class AccountsViewModel(
    private val accounts: AccountRepository,
    private val sync: SyncController,
) : ViewModel() {

    /** Null until the database answers, so the UI can tell "loading" from "no profiles". */
    val profiles: StateFlow<List<ProfileEntity>?> =
        accounts.allProfiles.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val syncs: StateFlow<Map<Long, SyncController.State>> = sync.states

    fun sync(profileId: Long) = sync.sync(profileId)

    fun remove(profileId: Long) {
        viewModelScope.launch { accounts.removeProfile(profileId) }
    }
}
