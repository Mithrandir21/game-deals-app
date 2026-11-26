package pm.bam.gamedeals.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlin.random.Random
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
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleReminder
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.error

internal data class WinterSaleGuideState(
    val event: WinterSaleEventState = WinterSaleEventState.Hidden(pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleHiddenReason.Unavailable),
    val content: WinterSaleGuideContent? = null,
    val country: Country? = null,
    val includeAdult: Boolean = false,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val stalePrices: Boolean = false,
    /** False until the first event check returns, so the screen shows a spinner rather than "not available". */
    val eventChecked: Boolean = false,
)

internal class WinterSaleGuideViewModel(
    private val events: WinterSaleEventRepository,
    private val guide: WinterSaleGuideRepository,
    region: RegionRepository,
    settings: SettingsRepository,
    auth: AuthTokenStore,
    private val clock: Clock,
    private val reminder: WinterSaleReminder,
    private val logger: Logger,
    /** Spreads the sale-start price reload of every open guide, so they don't all call ITAD at 18:00:00 UTC. */
    private val saleStartJitterMillis: () -> Long = { Random.nextLong(0, MAX_SALE_START_JITTER_MS + 1) },
) : ViewModel() {
    private val mutableState = MutableStateFlow(WinterSaleGuideState())
    val state: StateFlow<WinterSaleGuideState> = mutableState

    private data class Context(val country: Country, val includeAdult: Boolean, val account: String?) {
        fun samePrices(other: Context) = country == other.country && account == other.account
    }

    private var currentContext: Context? = null
    private var loadedContext: Context? = null
    /** The sale phase the shown prices were loaded in; a change means they must be reloaded. */
    private var loadedPhase: WinterSalePhase? = null
    private var lastSuccessfulAtEpochMs = 0L
    private var lastLoadStartedAtEpochMs: Long? = null
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
                    loadedPhase = null
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

    /** A pull straight after a load is ignored: repeated pulls would each cost about five ITAD calls on the app's one key. */
    fun onPullToRefresh() {
        val now = clock.nowMillis()
        val last = lastLoadStartedAtEpochMs
        if (last != null && now >= last && now - last < PULL_GAP_MS) return
        refresh(force = true)
    }

    /** "Try again" on the unavailable screen: show the spinner while the event is checked again. */
    fun onRetry() {
        mutableState.value = mutableState.value.copy(eventChecked = false)
        refresh(force = true)
    }

    fun refresh(force: Boolean = true) {
        load?.cancel()
        load = viewModelScope.launch {
            val context = currentContext ?: return@launch
            val old = mutableState.value
            val event = try { events.refresh() } catch (error: CancellationException) { throw error } catch (t: Throwable) {
                error(logger, t)
                WinterSaleEventState.Hidden(pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleHiddenReason.Unavailable)
            }
            viewModelScope.launch { reminder.reconcile(event) }
            if (context != currentContext) return@launch
            if (event !is WinterSaleEventState.Available) {
                loadedContext = null
                loadedPhase = null
                lastSuccessfulAtEpochMs = 0L
                mutableState.value = mutableState.value.copy(event = event, eventChecked = true, content = null, loading = false,
                    refreshing = false, stalePrices = false)
                return@launch
            }
            val phaseChanged = loadedPhase != event.phase
            val sameContext = loadedContext?.samePrices(context) == true
            val stillFresh = lastSuccessfulAtEpochMs > 0L && clock.nowMillis() >= lastSuccessfulAtEpochMs &&
                clock.nowMillis() - lastSuccessfulAtEpochMs < REFRESH_MS
            if (!force && sameContext && stillFresh && !phaseChanged && old.content != null) {
                mutableState.value = mutableState.value.copy(event = event, eventChecked = true, loading = false, refreshing = false)
                return@launch
            }
            if (!force && loadedPhase == WinterSalePhase.BeforeSale && event.phase == WinterSalePhase.OnSale && old.content != null) {
                // The sale copy and its "prices may still be updating" note show at once; the price reload waits a moment.
                mutableState.value = mutableState.value.copy(event = event, eventChecked = true)
                delay(saleStartJitterMillis())
                if (context != currentContext) return@launch
            }
            // While reloading, judge the shown prices by their own check time: a retry after a partial load must not
            // hide prices fetched moments ago.
            val shownPricesFresh = old.content?.checkedAtEpochMillis?.let { checkedAt ->
                clock.nowMillis() >= checkedAt && clock.nowMillis() - checkedAt < REFRESH_MS
            } == true
            mutableState.value = mutableState.value.copy(event = event, eventChecked = true, loading = old.content == null,
                refreshing = old.content != null, stalePrices = old.content != null && !shownPricesFresh)
            lastLoadStartedAtEpochMs = clock.nowMillis()
            val result = try { guide.load(context.country.code, context.includeAdult, event.event.saleStartEpochMillis,
                event.phase == WinterSalePhase.OnSale) }
            catch (error: CancellationException) { throw error } catch (t: Throwable) {
                error(logger, t)
                null
            }
            if (context != currentContext) return@launch
            if (result == null || (!result.hasCards && result.hasFailure && old.content != null && sameContext)) {
                mutableState.value = mutableState.value.copy(content = if (sameContext) old.content else null,
                    loading = false, refreshing = false, stalePrices = sameContext && old.content != null)
                return@launch
            }
            if (result != null) {
                loadedContext = context
                loadedPhase = event.phase
                if (!result.hasFailure) lastSuccessfulAtEpochMs = clock.nowMillis()
                mutableState.value = mutableState.value.copy(content = result, loading = false, refreshing = false, stalePrices = false)
            } else {
                mutableState.value = mutableState.value.copy(content = null, loading = false, refreshing = false)
            }
        }
    }

    private companion object {
        const val REFRESH_MS = 15 * 60 * 1000L
        const val MAX_SALE_START_JITTER_MS = 10 * 60 * 1000L
        const val PULL_GAP_MS = 30 * 1000L
    }
}
