package pm.bam.gamedeals.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.auth.AuthTokenStore
import pm.bam.gamedeals.domain.models.AuthState
import pm.bam.gamedeals.domain.models.Country
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
import pm.bam.gamedeals.domain.repositories.settings.SettingsRepository
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleEventRepository
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleEventState
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleGuideContent
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleGuideRepository
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSalePhase

internal data class WinterSaleGuideState(
    val event: WinterSaleEventState = WinterSaleEventState.Hidden(pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleHiddenReason.Unavailable),
    val content: WinterSaleGuideContent? = null,
    val country: Country? = null,
    val includeAdult: Boolean = false,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val stalePrices: Boolean = false,
)

internal class WinterSaleGuideViewModel(
    private val events: WinterSaleEventRepository,
    private val guide: WinterSaleGuideRepository,
    region: RegionRepository,
    settings: SettingsRepository,
    auth: AuthTokenStore,
    private val clock: Clock,
) : ViewModel() {
    private val mutableState = MutableStateFlow(WinterSaleGuideState())
    val state: StateFlow<WinterSaleGuideState> = mutableState

    private data class Context(val country: Country, val includeAdult: Boolean, val account: String?) {
        fun samePrices(other: Context) = country == other.country && account == other.account
    }

    private var currentContext: Context? = null
    private var loadedContext: Context? = null
    private var lastSuccessfulAtEpochMs = 0L
    private var poll: Job? = null
    private var load: Job? = null

    init {
        viewModelScope.launch {
            combine(region.observeSelectedCountry(), settings.observeMatureOptIn(), auth.observeAuthState()) { country, adult, account ->
                Context(country, adult, (account as? AuthState.LoggedIn)?.username)
            }.collect { next ->
                val previous = currentContext
                currentContext = next
                if (previous == null || !previous.samePrices(next)) {
                    load?.cancel()
                    loadedContext = null
                    lastSuccessfulAtEpochMs = 0L
                    mutableState.value = mutableState.value.copy(content = null, country = next.country, includeAdult = next.includeAdult,
                        stalePrices = false, loading = false, refreshing = false)
                    if (poll?.isActive == true) refresh(force = true)
                } else if (previous.includeAdult != next.includeAdult) {
                    mutableState.value = mutableState.value.copy(includeAdult = next.includeAdult)
                    if (next.includeAdult && poll?.isActive == true) refresh(force = true)
                }
            }
        }
    }

    fun onResume() {
        if (poll?.isActive == true) return
        poll = viewModelScope.launch {
            while (isActive) {
                refresh(force = false)
                load?.join()
                val available = mutableState.value.event as? WinterSaleEventState.Available
                val deadline = available?.let {
                    if (it.phase == WinterSalePhase.BeforeSale) it.event.saleStartEpochMillis else it.event.endEpochMillis
                }
                val remaining = deadline?.minus(available.observedServerNowEpochMillis)
                delay(if (remaining != null) minOf(REFRESH_MS, remaining.coerceAtLeast(1_000L)) else REFRESH_MS)
            }
        }
    }

    fun onPause() { poll?.cancel(); poll = null }

    fun refresh(force: Boolean = true) {
        load?.cancel()
        load = viewModelScope.launch {
            val context = currentContext ?: return@launch
            val old = mutableState.value
            val event = try { events.refresh() } catch (error: CancellationException) { throw error } catch (_: Throwable) {
                WinterSaleEventState.Hidden(pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleHiddenReason.Unavailable)
            }
            if (context != currentContext) return@launch
            if (event !is WinterSaleEventState.Available) {
                loadedContext = null
                lastSuccessfulAtEpochMs = 0L
                mutableState.value = mutableState.value.copy(event = event, content = null, loading = false, refreshing = false, stalePrices = false)
                return@launch
            }
            val phaseChanged = (old.event as? WinterSaleEventState.Available)?.phase != event.phase
            val sameContext = loadedContext?.samePrices(context) == true
            val stillFresh = lastSuccessfulAtEpochMs > 0L && clock.nowMillis() >= lastSuccessfulAtEpochMs &&
                clock.nowMillis() - lastSuccessfulAtEpochMs < REFRESH_MS
            if (!force && sameContext && stillFresh && !phaseChanged && old.content != null) {
                mutableState.value = mutableState.value.copy(event = event, loading = false, refreshing = false)
                return@launch
            }
            mutableState.value = mutableState.value.copy(event = event, loading = old.content == null,
                refreshing = old.content != null, stalePrices = old.content != null && !stillFresh)
            val result = try { guide.load(context.country.code, context.includeAdult, event.event.saleStartEpochMillis,
                event.phase == WinterSalePhase.OnSale) }
            catch (error: CancellationException) { throw error } catch (_: Throwable) { null }
            if (context != currentContext) return@launch
            if (result == null || (!result.hasCards && result.hasFailure && old.content != null && sameContext)) {
                mutableState.value = mutableState.value.copy(content = if (sameContext) old.content else null,
                    loading = false, refreshing = false, stalePrices = sameContext && old.content != null)
                return@launch
            }
            if (result != null) {
                loadedContext = context
                if (!result.hasFailure) lastSuccessfulAtEpochMs = clock.nowMillis()
                mutableState.value = mutableState.value.copy(content = result, loading = false, refreshing = false, stalePrices = false)
            } else {
                mutableState.value = mutableState.value.copy(content = null, loading = false, refreshing = false)
            }
        }
    }

    private companion object { const val REFRESH_MS = 15 * 60 * 1000L }
}
