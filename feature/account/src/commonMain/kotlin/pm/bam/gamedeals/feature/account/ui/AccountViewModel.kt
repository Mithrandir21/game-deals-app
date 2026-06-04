package pm.bam.gamedeals.feature.account.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pm.bam.gamedeals.domain.models.AuthState
import pm.bam.gamedeals.domain.models.CollectionEntry
import pm.bam.gamedeals.domain.models.WaitlistEntry
import pm.bam.gamedeals.domain.repositories.account.AccountRepository
import pm.bam.gamedeals.domain.repositories.collection.CollectionRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.fatal

/**
 * Drives the Account tab (epic #219, Phase 2.4): observes the ITAD auth state, runs login/logout, and
 * loads the user's waitlist + collection once signed in. The auth-state observer is the single source
 * of truth — [onLogin] just triggers the OAuth flow; the resulting `LoggedIn` emission loads the lists.
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
        viewModelScope.launch {
            accountRepository.observeAuthState().collect { auth ->
                when (auth) {
                    is AuthState.LoggedOut -> uiState.update {
                        it.copy(loggedIn = false, username = "", waitlist = persistentListOf(), collection = persistentListOf())
                    }
                    is AuthState.LoggedIn -> {
                        uiState.update { it.copy(loggedIn = true, username = auth.username) }
                        loadLists()
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

    private suspend fun loadLists() {
        val waitlist = runCatching { waitlistRepository.getWaitlist() }.getOrElse { fatal(logger, it); emptyList() }
        val collection = runCatching { collectionRepository.getCollection() }.getOrElse { fatal(logger, it); emptyList() }
        uiState.update { it.copy(waitlist = waitlist.toImmutableList(), collection = collection.toImmutableList()) }
    }

    @Immutable
    data class AccountScreenData(
        val loggedIn: Boolean = false,
        val username: String = "",
        val loggingIn: Boolean = false,
        val waitlist: ImmutableList<WaitlistEntry> = persistentListOf(),
        val collection: ImmutableList<CollectionEntry> = persistentListOf(),
    )
}
