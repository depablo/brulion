package pl.brulion.ui.messages

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pl.brulion.data.MessagesRepository
import pl.brulion.data.db.BrulionDatabase
import pl.brulion.data.db.SentMessageEntity
import pl.brulion.ui.common.UiError
import pl.brulion.ui.common.toUiError

/** A sent message, shown from the sent folder. Always fetched on open, so the read state is current. */
class SentMessageViewModel(
    db: BrulionDatabase,
    private val repository: MessagesRepository,
    private val profileId: Long,
    private val messageId: Long,
) : ViewModel() {

    val message: StateFlow<SentMessageEntity?> = db.sentMessageDao().observe(profileId, messageId).stateIn()

    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<UiError?>(null)
        private set

    init {
        refresh()
    }

    fun refresh() {
        if (loading) return
        loading = true
        error = null
        viewModelScope.launch {
            try {
                repository.openSent(profileId, messageId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.toUiError()
            }
            loading = false
        }
    }

    private fun <T> Flow<T>.stateIn(): StateFlow<T?> = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
