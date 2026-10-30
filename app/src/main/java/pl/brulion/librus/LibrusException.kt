package pl.brulion.librus

class LibrusException(
    val reason: Reason,
    val detail: String? = null,
    cause: Throwable? = null,
) : Exception(reason.name + (detail?.let { ": $it" } ?: ""), cause) {

    enum class Reason {
        NETWORK,
        /** Stored credentials no longer work; the user has to log in again. */
        REAUTH_REQUIRED,
        /** The Synergia account needs an action on the Librus portal (e.g. reconnecting). */
        ACCOUNT_DISCONNECTED,
        TOKEN_EXPIRED,
        MAINTENANCE,
        ACCESS_DENIED,
        NOT_FOUND,
        /** The school has disabled this module (e.g. lucky number). */
        FEATURE_DISABLED,
        /** A message was posted but Librus didn't confirm it; it may have been sent anyway. */
        SEND_UNCONFIRMED,
        /** Synergia's message forms no longer look the way the app expects. */
        FORM_CHANGED,
        UNKNOWN,
    }
}
