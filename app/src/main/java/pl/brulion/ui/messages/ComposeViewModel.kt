package pl.brulion.ui.messages

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import pl.brulion.data.MessagesRepository
import pl.brulion.librus.Recipient
import pl.brulion.librus.RecipientGroup
import pl.brulion.ui.common.UiError
import pl.brulion.ui.common.toUiError

/** Sending runs in [appScope], so rotating the screen or leaving the app doesn't cut it off halfway. */
class ComposeViewModel(
    private val repository: MessagesRepository,
    private val appScope: CoroutineScope,
    private val profileId: Long,
) : ViewModel() {

    var groups by mutableStateOf<List<RecipientGroup>?>(null)
        private set
    var group by mutableStateOf<RecipientGroup?>(null)
        private set
    var recipients by mutableStateOf<List<Recipient>?>(null)
        private set
    var selected by mutableStateOf<Set<Recipient>>(emptySet())
        private set
    var topic by mutableStateOf("")
    var text by mutableStateOf("")
    var loading by mutableStateOf(false)
        private set
    var sending by mutableStateOf(false)
        private set
    var sent by mutableStateOf(false)
        private set
    var error by mutableStateOf<UiError?>(null)
        private set

    private var recipientsJob: Job? = null

    val canSend: Boolean get() = selected.isNotEmpty() && topic.isNotBlank() && text.isNotBlank() && !sending

    init {
        loadGroups()
    }

    fun loadGroups() {
        run {
            groups = repository.recipientGroups(profileId)
            groups?.firstOrNull()?.let(::selectGroup)
        }
    }

    /** Recipients from different groups can't be mixed in one Synergia message. */
    fun selectGroup(newGroup: RecipientGroup) {
        if (newGroup == group && recipients != null) return
        group = newGroup
        recipients = null
        selected = emptySet()
        recipientsJob?.cancel()
        recipientsJob = run { recipients = repository.recipients(profileId, newGroup) }
    }

    fun toggle(recipient: Recipient) {
        selected = if (recipient in selected) selected - recipient else selected + recipient
    }

    fun send() {
        val target = group ?: return
        if (!canSend) return
        sending = true
        error = null
        appScope.launch {
            try {
                repository.send(profileId, target, selected.toList(), topic.trim(), text.trim())
                sent = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.toUiError()
            }
            sending = false
        }
    }

    /** Loads in flight; groups start a recipients load of their own, so one flag isn't enough. */
    private var activeLoads = 0

    private fun run(block: suspend () -> Unit): Job {
        activeLoads++
        loading = true
        error = null
        return viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.toUiError()
            } finally {
                activeLoads--
                loading = activeLoads > 0
            }
        }
    }
}
