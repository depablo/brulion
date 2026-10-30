package pl.brulion.ui.common

import androidx.annotation.StringRes
import pl.brulion.R
import pl.brulion.librus.LibrusException
import pl.brulion.librus.LibrusException.Reason

/** A user-facing error: a translated message plus the raw Librus detail for debugging. */
data class UiError(@StringRes val message: Int, val detail: String? = null)

fun Throwable.toUiError(): UiError {
    val librus = this as? LibrusException ?: return UiError(R.string.error_unknown, message)
    val message = when (librus.reason) {
        Reason.NETWORK -> R.string.error_network
        Reason.REAUTH_REQUIRED, Reason.TOKEN_EXPIRED -> R.string.error_reauth
        Reason.ACCOUNT_DISCONNECTED -> R.string.error_account_disconnected
        Reason.MAINTENANCE -> R.string.error_maintenance
        Reason.ACCESS_DENIED -> R.string.error_access_denied
        Reason.NOT_FOUND -> R.string.error_not_found
        Reason.FEATURE_DISABLED -> R.string.error_feature_disabled
        Reason.SEND_UNCONFIRMED -> R.string.error_send_unconfirmed
        Reason.FORM_CHANGED -> R.string.error_form_changed
        Reason.UNKNOWN -> R.string.error_unknown
    }
    return UiError(message, librus.detail)
}
