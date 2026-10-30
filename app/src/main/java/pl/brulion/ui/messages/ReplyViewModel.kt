package pl.brulion.ui.messages

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import pl.brulion.data.MessagesRepository
import pl.brulion.ui.common.UiError
import pl.brulion.ui.common.toUiError

/** Sending runs in [appScope], so rotating the screen or leaving the app doesn't cut it off halfway. */
class ReplyViewModel(
    private val repository: MessagesRepository,
    private val appScope: CoroutineScope,
    private val profileId: Long,
    private val messageId: Long,
) : ViewModel() {

    var text by mutableStateOf("")
    var sending by mutableStateOf(false)
        private set
    var sent by mutableStateOf(false)
        private set
    var error by mutableStateOf<UiError?>(null)
        private set

    fun send() {
        if (sending || text.isBlank()) return
        sending = true
        error = null
        appScope.launch {
            try {
                repository.reply(profileId, messageId, text.trim())
                sent = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.toUiError()
            }
            sending = false
        }
    }
}
