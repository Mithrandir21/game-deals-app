package pm.bam.gamedeals.feature.onboarding.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pm.bam.gamedeals.domain.models.Country
import pm.bam.gamedeals.domain.repositories.account.AccountRepository
import pm.bam.gamedeals.domain.repositories.notifications.NotificationSettings
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
import pm.bam.gamedeals.domain.repositories.settings.SettingsRepository
import pm.bam.gamedeals.domain.scheduling.NotificationScheduler
import pm.bam.gamedeals.feature.onboarding.platform.RegionDetector
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.fatal

/**
 * Drives the first-run onboarding carousel. Three of the slides are interactive setup steps:
 *
 * - **Region** — on a genuine first run (onboarding not yet completed) the device locale is mapped to a
 *   [supported region][RegionRepository.supportedCountries] and applied, so prices aren't silently shown in
 *   USD. The user can still change it via the picker. Replays from the Account hub don't re-detect, so a
 *   region the user chose earlier is never overwritten.
 * - **Notifications** — persists the background-alerts opt-in and arms the poll. The OS permission prompt is
 *   owned by the composable; [onNotificationsEnabled] is only called once permission is granted. Enabling
 *   pre-login is safe: the auth-state lifecycle re-arms the poll once the user signs in.
 * - **Sign in** — runs the ITAD OAuth flow ([AccountRepository.login], which suspends across the browser
 *   round-trip). The onboarding screen is kept in the back stack until it returns, then finishes to Home.
 *
 * [finish]/[signInThenFinish] persist the completed flag **before** invoking the navigation callback in the
 * same coroutine, so the write isn't lost to the screen being torn down.
 */
internal class OnboardingViewModel(
    private val regionRepository: RegionRepository,
    private val settingsRepository: SettingsRepository,
    private val notificationSettings: NotificationSettings,
    private val notificationScheduler: NotificationScheduler,
    private val accountRepository: AccountRepository,
    private val regionDetector: RegionDetector,
    private val logger: Logger,
) : ViewModel() {

    val uiState: StateFlow<OnboardingState>
        field = MutableStateFlow(OnboardingState())

    /** The full region list for the picker — static reference data, not part of the reactive state. */
    val countries: ImmutableList<Country> = regionRepository.supportedCountries.toImmutableList()

    init {
        // On a true first run, pre-select the storefront region from the device locale (a replay from the
        // Account hub leaves an already-chosen region untouched).
        viewModelScope.launch {
            if (!settingsRepository.getOnboardingCompleted()) {
                val detected = regionDetector.detectCountryCode()
                    ?.let { code -> countries.firstOrNull { it.code.equals(code, ignoreCase = true) } }
                if (detected != null) regionRepository.setSelectedCountry(detected)
            }
        }

        viewModelScope.launch {
            regionRepository.observeSelectedCountry().collect { country ->
                uiState.update { it.copy(selectedCountry = country) }
            }
        }

        viewModelScope.launch {
            notificationSettings.observeEnabled().collect { enabled ->
                uiState.update { it.copy(notificationsEnabled = enabled) }
            }
        }
    }

    fun onCountrySelected(country: Country) {
        viewModelScope.launch { regionRepository.setSelectedCountry(country) }
    }

    /** Called by the composable only after the OS notification permission has been granted. */
    fun onNotificationsEnabled() {
        viewModelScope.launch {
            notificationSettings.setEnabled(true)
            notificationScheduler.schedule()
        }
    }

    /** Finish onboarding without signing in (Skip, or the final "Maybe later"). */
    fun finish(onFinished: () -> Unit) {
        viewModelScope.launch {
            settingsRepository.setOnboardingCompleted(true)
            onFinished()
        }
    }

    /**
     * Mark onboarding complete, then run the OAuth login. [login][AccountRepository.login] suspends across
     * the browser round-trip; only once it resolves (success, cancel, or error) do we navigate via
     * [onFinished], so the screen — and this scope — stays alive for the duration.
     */
    fun signInThenFinish(onFinished: () -> Unit) {
        viewModelScope.launch {
            settingsRepository.setOnboardingCompleted(true)
            uiState.update { it.copy(signingIn = true) }
            try {
                accountRepository.login()
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                fatal(logger, t)
            } finally {
                uiState.update { it.copy(signingIn = false) }
            }
            onFinished()
        }
    }

    @Immutable
    data class OnboardingState(
        /** The storefront region shown on the region step and pre-selected in the picker. */
        val selectedCountry: Country? = null,
        /** Whether background sale alerts are enabled (reflects the persisted opt-in). */
        val notificationsEnabled: Boolean = false,
        /** True while the OAuth browser round-trip is in flight. */
        val signingIn: Boolean = false,
    )
}
