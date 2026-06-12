package pm.bam.gamedeals.feature.account.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pm.bam.gamedeals.domain.models.AuthState
import pm.bam.gamedeals.domain.repositories.account.AccountRepository
import pm.bam.gamedeals.domain.repositories.collection.CollectionRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.fatal

/**
 * Drives the Account hub (epic #272, P1.2): observes the ITAD auth state and exposes the data the hub
 * needs — login status, profile, the reconnect prompt (#273), and the Waitlist/Collection counts. The
 * waitlist/collection *lists* now live in their own sub-screens; the hub only shows counts, sourced
 * reactively from the Room-backed id sets (no network) while the per-list reconcile is deferred to those
 * sub-screens. A fresh `LoggedIn` emission triggers one [reconcileLibrary] so the counts are current.
 */
internal class AccountViewModel(
    private val accountRepository: AccountRepository,
    private val waitlistRepository: WaitlistRepository,
    private val collectionRepository: CollectionRepository,
    private val logger: Logger,
) : ViewModel() {

    val uiState: StateFlow<AccountScreenData>
        field = MutableStateFlow(AccountScreenData())

    init {
        // Reactive, Room-backed library counts. The id sets are auth-gated (empty when logged out), so
        // the counts zero out on logout without any extra wiring.
        viewModelScope.launch {
            combine(
                waitlistRepository.observeWaitlistIds(),
                collectionRepository.observeCollectionIds(),
            ) { waitlistIds, collectionIds -> waitlistIds.size to collectionIds.size }
                .collect { (waitlistCount, collectionCount) ->
                    uiState.update { it.copy(waitlistCount = waitlistCount, collectionCount = collectionCount) }
                }
        }

        viewModelScope.launch {
            accountRepository.observeAuthState().collect { auth ->
                when (auth) {
                    is AuthState.LoggedOut -> uiState.update {
                        it.copy(loggedIn = false, username = "", needsReconnect = false)
                    }
                    is AuthState.LoggedIn -> {
                        uiState.update { it.copy(loggedIn = true, username = auth.username, needsReconnect = auth.needsReconnect) }
                        reconcileLibrary()
                    }
                }
            }
        }
    }

    fun onLogin() {
        viewModelScope.launch {
            uiState.update { it.copy(loggingIn = true) }
            try {
                accountRepository.login() // success flows through observeAuthState()
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                fatal(logger, t)
            } finally {
                uiState.update { it.copy(loggingIn = false) }
            }
        }
    }

    fun onLogout() {
        viewModelScope.launch { accountRepository.logout() }
    }

    /** Refreshes the Room-backed id sets from ITAD (remote-as-truth) on login; counts then flow reactively. */
    private suspend fun reconcileLibrary() {
        runCatching { waitlistRepository.getWaitlist() }.onFailure { fatal(logger, it) }
        runCatching { collectionRepository.getCollection() }.onFailure { fatal(logger, it) }
    }

    @Immutable
    data class AccountScreenData(
        val loggedIn: Boolean = false,
        val username: String = "",
        val loggingIn: Boolean = false,
        /** The stored token predates the current OAuth scope set — prompt a reconnect (#273). */
        val needsReconnect: Boolean = false,
        val waitlistCount: Int = 0,
        val collectionCount: Int = 0,
        /** Unread ITAD notifications; populated in P2 (#277/#278), 0 until then. */
        val unreadNotifications: Int = 0,
        /** Whether a Steam profile is linked; populated in P5 (#285/#286), false until then. */
        val linkedSteam: Boolean = false,
    )
}
