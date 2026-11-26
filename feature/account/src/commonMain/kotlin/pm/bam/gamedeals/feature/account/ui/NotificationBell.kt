package pm.bam.gamedeals.feature.account.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import pm.bam.gamedeals.domain.models.AuthState
import pm.bam.gamedeals.domain.repositories.account.AccountRepository
import pm.bam.gamedeals.domain.repositories.notifications.NotificationsRepository

/**
 * App-wide notification state for the shared toolbar's bell action (epic #272, P2.2 #278). Lives at
 * the shell level (not the Account screen) so the bell is populated regardless of the active tab: it
 * refreshes notifications whenever the user is logged in, then exposes the reactive unread tally.
 */
internal class NotificationBellViewModel(
    private val notificationsRepository: NotificationsRepository,
    accountRepository: AccountRepository,
) : ViewModel() {

    /** Unread **games**, in the same unit the Notifications list shows ("· N games" on unread days). */
    val unreadCount: StateFlow<Int> = notificationsRepository.observeUnreadGameCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * Drives the bell's presence. [NotificationsRepository.observeUnreadGameCount] is itself auth-gated and
     * emits `0` both when logged out *and* when logged in with nothing unread, so it can't answer
     * "is there an account at all" — visibility needs its own auth read.
     */
    val loggedIn: StateFlow<Boolean> = accountRepository.observeAuthState()
        .map { it is AuthState.LoggedIn }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        viewModelScope.launch {
            accountRepository.observeAuthState().collect { auth ->
                // Load the list, then resolve the unread days' games — the badge counts games, which aren't
                // known until each notification's detail is fetched.
                if (auth is AuthState.LoggedIn) runCatching {
                    notificationsRepository.getNotifications()
                    notificationsRepository.resolveUnreadGames()
                }
            }
        }
    }
}

/** What the shared toolbar needs to render the notification bell: whether to show it, and the badge tally. */
@Immutable
data class NotificationBellState(
    val visible: Boolean = false,
    val unreadCount: Int = 0,
)

/**
 * Remembers the app-wide notification-bell state for the shared toolbar (#278). Exported from this feature
 * module (rather than read inside `:common:ui`) so the shell stays free of a dependency on `:feature:account`
 * — the hosts pass the primitives down.
 */
@Composable
fun rememberNotificationBellState(): NotificationBellState {
    val viewModel: NotificationBellViewModel = koinViewModel()
    val loggedIn by viewModel.loggedIn.collectAsStateWithLifecycle()
    val count by viewModel.unreadCount.collectAsStateWithLifecycle()
    return NotificationBellState(visible = loggedIn, unreadCount = count)
}
