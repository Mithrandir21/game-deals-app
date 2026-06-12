package pm.bam.gamedeals.domain.models

import androidx.compose.runtime.Immutable

/**
 * Whether the user is authenticated with their IsThereAnyDeal account (epic #219, Phase 2).
 * Drives the account-gated UI (stat cards, the login-gated waitlist heart).
 */
@Immutable
sealed interface AuthState {
    data object LoggedOut : AuthState

    /**
     * @param needsReconnect true when the persisted token was granted under an older OAuth scope set
     * (see [pm.bam.gamedeals.domain.auth.CURRENT_SCOPE_VERSION]) and the user should re-authenticate to
     * unlock newer account features (#273). The session is still usable for the scopes it already holds.
     */
    data class LoggedIn(val username: String, val needsReconnect: Boolean = false) : AuthState
}
