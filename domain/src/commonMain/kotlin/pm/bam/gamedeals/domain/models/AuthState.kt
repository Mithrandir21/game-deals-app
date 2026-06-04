package pm.bam.gamedeals.domain.models

import androidx.compose.runtime.Immutable

/**
 * Whether the user is authenticated with their IsThereAnyDeal account (epic #219, Phase 2).
 * Drives the account-gated UI (stat cards, the login-gated waitlist heart).
 */
@Immutable
sealed interface AuthState {
    data object LoggedOut : AuthState
    data class LoggedIn(val username: String) : AuthState
}
