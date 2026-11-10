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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
import pm.bam.gamedeals.domain.repositories.settings.SettingsRepository
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleEventRepository
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleEventState
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleHiddenReason
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleHub
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleHubRepository
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSalePhase
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleReminder
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.analytics.Analytics
import pm.bam.gamedeals.logging.analytics.AnalyticsEvents
import pm.bam.gamedeals.logging.error

internal data class WinterSaleHubState(
    val event: WinterSaleEventState = WinterSaleEventState.Hidden(WinterSaleHiddenReason.Unavailable),
    /** False until the first event check returns, so the entry shows a spinner rather than picking a page too early. */
    val eventChecked: Boolean = false,
    val hub: WinterSaleHub? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    /** Nothing could load and there is nothing older to show. */
    val failed: Boolean = false,
    /** The sale's end on this phone's clock, so the sale lists count down by the server's time like the cards do. */
    val saleEndOnPhoneEpochMillis: Long? = null,
) {
    /** The hub only replaces the guide while the sale is on; before it, and after it, the guide's own pages show. */
    val onSale: Boolean get() = (event as? WinterSaleEventState.Available)?.phase == WinterSalePhase.OnSale
}

/**
 * The guide's entry while the sale is on: checks the event like the guide does, and loads the hub's shelves
 * (store tiles, all-time lows, big cuts). Shelves come from a 30-minute cache, so the 15-minute re-check is cheap.
 */
internal class WinterSaleHubViewModel(
    private val events: WinterSaleEventRepository,
    private val hubs: WinterSaleHubRepository,
    region: RegionRepository,
    settings: SettingsRepository,
    private val reminder: WinterSaleReminder,
    private val analytics: Analytics,
    private val clock: Clock,
    private val logger: Logger,
    /** Spreads the hub loads of every phone that is watching when the sale starts, so they don't all call ITAD at once. */
    private val saleStartJitterMillis: () -> Long = { Random.nextLong(0, MAX_SALE_START_JITTER_MS + 1) },
) : ViewModel() {
    private val mutableState = MutableStateFlow(WinterSaleHubState())
    val state: StateFlow<WinterSaleHubState> = mutableState

    private data class Context(val country: String, val includeAdult: Boolean)

    private var context: Context? = null
    private var poll: Job? = null
    private var load: Job? = null
    private var lastLoadStartedAtEpochMs: Long? = null
    private var reportedOpen = false

    init {
        viewModelScope.launch {
            combine(region.observeSelectedCountry(), settings.observeMatureOptIn()) { country, adult -> Context(country.code, adult) }
                .collect { next ->
                    val previous = context
                    context = next
                    if (previous == next) return@collect
                    if (previous != null) {
                        load?.cancel()
                        mutableState.update { it.copy(hub = null, loading = false, refreshing = false, failed = false) }
                    }
                    // Also covers the region arriving after the screen opened: that first check stopped without it.
                    if (poll?.isActive == true) refresh(forceHub = false)
                }
        }
    }

    fun onResume() {
        if (poll?.isActive == true) return
        poll = viewModelScope.launch {
            while (isActive) {
                refresh(forceHub = false)
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

    fun onPause() {
        poll?.cancel()
        poll = null
    }

    /** A pull straight after a load is ignored: each hub load costs about eleven ITAD calls on the app's one key. */
    fun onPullToRefresh() {
        val now = clock.nowMillis()
        val last = lastLoadStartedAtEpochMs
        if (last != null && now >= last && now - last < PULL_GAP_MS) return
        refresh(forceHub = true)
    }

    fun onRetry() {
        mutableState.update { it.copy(failed = false) }
        refresh(forceHub = true)
    }

    fun onShelfOpened(shelfKey: String) {
        analytics.capture(AnalyticsEvents.WINTER_SALE_SHELF_OPENED, mapOf("shelf" to shelfKey))
    }

    fun onGamePeeked(shelfKey: String) {
        analytics.capture(AnalyticsEvents.WINTER_SALE_GAME_PEEKED, mapOf("shelf" to shelfKey, "from" to "hub"))
    }

    private fun refresh(forceHub: Boolean) {
        load?.cancel()
        load = viewModelScope.launch {
            val event = try {
                events.refresh()
            } catch (error: CancellationException) {
                throw error
            } catch (t: Throwable) {
                error(logger, t)
                WinterSaleEventState.Hidden(WinterSaleHiddenReason.Unavailable)
            }
            val endOnPhone = (event as? WinterSaleEventState.Available)
                ?.let { it.event.endEpochMillis - it.observedServerNowEpochMillis + clock.nowMillis() }
            viewModelScope.launch { reminder.reconcile(event) }

            val wasBeforeSale = (mutableState.value.event as? WinterSaleEventState.Available)?.phase == WinterSalePhase.BeforeSale
            val nowOnSale = (event as? WinterSaleEventState.Available)?.phase == WinterSalePhase.OnSale
            if (wasBeforeSale && nowOnSale && !forceHub) {
                // Watching as the sale starts: stay on the guide a moment so phones don't all load the hub at once.
                delay(saleStartJitterMillis())
            }
            if (!nowOnSale) {
                mutableState.update {
                    it.copy(event = event, eventChecked = true, hub = null, loading = false, refreshing = false, failed = false,
                        saleEndOnPhoneEpochMillis = endOnPhone)
                }
                return@launch
            }

            val target = context
            if (target == null) {
                // The region isn't known yet; its arrival starts this check again.
                mutableState.update { it.copy(event = event, eventChecked = true, loading = true, saleEndOnPhoneEpochMillis = endOnPhone) }
                return@launch
            }
            val shown = mutableState.value.hub?.takeIf { it.country == target.country && it.includeAdult == target.includeAdult }
            mutableState.update {
                it.copy(event = event, eventChecked = true, hub = shown, loading = shown == null, refreshing = shown != null && forceHub, failed = false,
                    saleEndOnPhoneEpochMillis = endOnPhone)
            }
            if (!reportedOpen) {
                reportedOpen = true
                analytics.capture(AnalyticsEvents.WINTER_SALE_HUB_OPENED)
            }

            lastLoadStartedAtEpochMs = clock.nowMillis()
            val loaded = try {
                hubs.load(target.country, target.includeAdult, force = forceHub)
            } catch (error: CancellationException) {
                throw error
            } catch (t: Throwable) {
                error(logger, t)
                null
            }
            if (target != context) return@launch

            val nothingLoaded = loaded == null || (loaded.isEmpty && loaded.partial)
            mutableState.update {
                when {
                    !nothingLoaded -> it.copy(hub = loaded, loading = false, refreshing = false, failed = false)
                    shown != null -> it.copy(hub = shown, loading = false, refreshing = false, failed = false)
                    else -> it.copy(hub = null, loading = false, refreshing = false, failed = true)
                }
            }
        }
    }

    private companion object {
        const val REFRESH_MS = 15 * 60 * 1000L
        const val MAX_SALE_START_JITTER_MS = 10 * 60 * 1000L
        const val PULL_GAP_MS = 30 * 1000L
    }
}
