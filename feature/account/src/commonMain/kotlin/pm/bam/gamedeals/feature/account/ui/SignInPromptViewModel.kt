package pm.bam.gamedeals.feature.account.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import pm.bam.gamedeals.domain.repositories.account.AccountRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.fatal

/**
 * Backs the shell-level sign-in prompt ([SignInPromptHost]). Runs the ITAD OAuth flow on demand and exposes
 * the in-flight flag for the button spinner; success flows through the app's auth state (AuthTokenStore)
 * like any other sign-in, so the gated surfaces re-enable themselves once the user returns signed in.
 */
internal class SignInPromptViewModel(
    private val accountRepository: AccountRepository,
    private val logger: Logger,
) : ViewModel() {

    val signingIn: StateFlow<Boolean>
        field = MutableStateFlow(false)

    /**
     * Runs OAuth ([AccountRepository.login] suspends across the browser round-trip); [onComplete] fires once
     * it resolves (success, cancel, or error) so the host can dismiss the sheet.
     */
    fun login(onComplete: () -> Unit) {
        viewModelScope.launch {
            signingIn.value = true
            try {
                accountRepository.login()
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                fatal(logger, t)
            } finally {
                signingIn.value = false
            }
            onComplete()
        }
    }
}
