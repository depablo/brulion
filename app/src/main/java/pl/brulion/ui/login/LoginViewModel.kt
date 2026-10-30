package pl.brulion.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import pl.brulion.data.AccountRepository
import pl.brulion.ui.common.UiError
import pl.brulion.ui.common.toUiError

class LoginViewModel(private val accounts: AccountRepository) : ViewModel() {

    sealed interface State {
        data object Idle : State
        data object Loading : State
        /** [needingAction]: students skipped because their account needs fixing on the portal. */
        data class Done(val needingAction: List<String> = emptyList()) : State
        data class Failed(val error: UiError) : State
    }

    var state by mutableStateOf<State>(State.Idle)
        private set

    fun loginWithPortalCode(code: String) = run { State.Done(accounts.addPortalAccount(code).needingAction) }

    fun failWith(error: UiError) {
        state = State.Failed(error)
    }

    fun dismissError() {
        if (state is State.Failed) state = State.Idle
    }

    private fun run(block: suspend () -> State.Done) {
        if (state == State.Loading || state is State.Done) return
        state = State.Loading
        viewModelScope.launch {
            state = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                State.Failed(e.toUiError())
            }
        }
    }
}
