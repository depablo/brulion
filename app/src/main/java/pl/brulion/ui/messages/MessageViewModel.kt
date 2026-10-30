package pl.brulion.ui.messages

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.net.Uri
import pl.brulion.data.Downloads
import pl.brulion.data.MessagesRepository
import pl.brulion.data.db.BrulionDatabase
import pl.brulion.data.db.MessageAttachmentEntity
import pl.brulion.data.db.MessageBodyEntity
import pl.brulion.data.db.InboxMessage
import pl.brulion.ui.common.UiError
import pl.brulion.ui.common.toUiError
import java.io.File

class MessageViewModel(
    db: BrulionDatabase,
    private val repository: MessagesRepository,
    private val downloadsFolder: Downloads,
    private val profileId: Long,
    private val messageId: Long,
) : ViewModel() {

    sealed interface Download {
        data object Running : Download
        /** Downloaded; [savedTo] is the copy in Downloads (null where that isn't possible). */
        data class Ready(val file: File, val savedTo: Uri?) : Download
        data class Failed(val error: UiError) : Download
    }

    val message: StateFlow<InboxMessage?> = db.messageDao().observeInboxMessage(profileId, messageId).stateIn(null)
    val body: StateFlow<MessageBodyEntity?> = db.messageDao().observeBody(profileId, messageId).stateIn(null)
    val attachments: StateFlow<List<MessageAttachmentEntity>> =
        db.messageDao().observeAttachments(profileId, messageId).stateIn(emptyList())

    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<UiError?>(null)
        private set
    var downloads by mutableStateOf<Map<Long, Download>>(emptyMap())
        private set

    init {
        refresh()
    }

    /** Always fetches on open: the body may have changed and the attachment links expire. */
    fun refresh() {
        if (loading) return
        loading = true
        error = null
        viewModelScope.launch {
            try {
                repository.open(profileId, messageId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.toUiError()
            }
            loading = false
        }
    }

    fun download(attachment: MessageAttachmentEntity) {
        // Already saved: nothing to do, the screen offers to open it.
        if (downloads[attachment.id] == Download.Running || downloads[attachment.id] is Download.Ready) return
        downloads = downloads + (attachment.id to Download.Running)
        viewModelScope.launch {
            val result = try {
                val file = repository.attachment(profileId, attachment)
                Download.Ready(file, if (downloadsFolder.isSupported) downloadsFolder.save(file) else null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Download.Failed(e.toUiError())
            }
            downloads = downloads + (attachment.id to result)
        }
    }

    private fun <T> kotlinx.coroutines.flow.Flow<T>.stateIn(initial: T): StateFlow<T> =
        stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)
}
