package pm.bam.gamedeals.feature.account.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import pm.bam.gamedeals.domain.models.AuthState
import pm.bam.gamedeals.domain.repositories.account.AccountRepository
import pm.bam.gamedeals.domain.repositories.notifications.NotificationsRepository

/**
 * App-wide unread-notifications count for the Account bottom-nav badge (epic #272, P2.2 #278). Lives at
 * the shell level (not the Account screen) so the badge is populated regardless of the active tab: it
 * refreshes notifications whenever the user is logged in, then exposes the reactive unread tally.
 */
internal class AccountTabBadgeViewModel(
    private val notificationsRepository: NotificationsRepository,
    accountRepository: AccountRepository,
) : ViewModel() {

    val unreadCount: StateFlow<Int> = notificationsRepository.observeUnreadCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        viewModelScope.launch {
            accountRepository.observeAuthState().collect { auth ->
                if (auth is AuthState.LoggedIn) runCatching { notificationsRepository.getNotifications() }
            }
        }
    }
}

/** Remembers the app-wide unread-notifications count for the Account tab badge (#278). */
@Composable
fun rememberAccountTabUnreadCount(): Int {
    val viewModel: AccountTabBadgeViewModel = koinViewModel()
    val count by viewModel.unreadCount.collectAsStateWithLifecycle()
    return count
}
