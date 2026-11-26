package pm.bam.gamedeals.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pm.bam.gamedeals.common.links.ShareLinks
import pm.bam.gamedeals.domain.models.AuthState
import pm.bam.gamedeals.domain.models.YearInDeals
import pm.bam.gamedeals.domain.repositories.account.AccountRepository
import pm.bam.gamedeals.domain.repositories.yearindeals.YearInDealsRepository
import pm.bam.gamedeals.domain.repositories.yearindeals.YearInDealsSampleMode
import pm.bam.gamedeals.logging.featureflags.FeatureFlag
import pm.bam.gamedeals.logging.featureflags.FeatureFlags
import kotlin.coroutines.cancellation.CancellationException

/**
 * Home's "Year in deals" entry: the year the card is about, shown to signed-in users while the card is in season and
 * its switch is on; null hides the entry. Follows the switch live, so turning it off hides the entry at once.
 */
internal class YearInDealsEntryViewModel(
    accountRepository: AccountRepository,
    private val yearInDeals: YearInDealsRepository,
    sampleMode: YearInDealsSampleMode,
    featureFlags: FeatureFlags,
) : ViewModel() {

    val offeredYear: StateFlow<Int?> = combine(
        accountRepository.observeAuthState(),
        sampleMode.observe(),
        featureFlags.observe(FeatureFlag.YearInDeals),
    ) { auth, sample, switchedOn ->
        // The developer screen's sample switch shows the entry any time, signed in or not.
        val offered = sample || (switchedOn && auth is AuthState.LoggedIn && yearInDeals.isOffered())
        if (offered) yearInDeals.reportYear() else null
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

/** The "Year in deals" screen: loads the card once, and again on retry. */
internal class YearInDealsViewModel(
    private val yearInDeals: YearInDealsRepository,
) : ViewModel() {

    sealed interface State {
        data object Loading : State
        data object Failed : State
        /** Signed in, but the year has nothing to show yet. */
        data class Empty(val year: Int) : State
        data class Ready(val year: YearInDeals) : State
    }

    private val mutableState = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = mutableState.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        mutableState.value = State.Loading
        viewModelScope.launch {
            mutableState.value = try {
                val year = yearInDeals.getYearInDeals()
                if (year.isEmpty) State.Empty(year.year) else State.Ready(year)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                State.Failed
            }
        }
    }

    /** The text that goes with the shared image: one line about the year and a link to get the app. */
    fun shareText(year: YearInDeals): String = buildString {
        append("My ${year.year} in game deals")
        year.collected?.let { append(": ${it.count} games collected") }
        append(". Made with Game Deals ")
        append(ShareLinks.APP_URL)
    }
}
