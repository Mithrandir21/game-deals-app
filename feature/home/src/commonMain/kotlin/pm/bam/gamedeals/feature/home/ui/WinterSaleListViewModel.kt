package pm.bam.gamedeals.feature.home.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
import pm.bam.gamedeals.domain.repositories.settings.SettingsRepository
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleDeal
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleHubRepository
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleShelf
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.analytics.Analytics
import pm.bam.gamedeals.logging.analytics.AnalyticsEvents
import pm.bam.gamedeals.logging.error

internal data class WinterSaleListState(
    val shelf: WinterSaleShelf?,
    val saleEndEpochMillis: Long?,
    val storeName: String? = null,
    val storeDeals: Int? = null,
    val deals: ImmutableList<WinterSaleDeal> = persistentListOf(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    /** The first page failed; the screen offers a retry instead of an empty list. */
    val failed: Boolean = false,
    /** A later page failed; the rows already shown stay and the footer offers a retry. */
    val moreFailed: Boolean = false,
    val endReached: Boolean = false,
)

/** One hub shelf in full ("See all"): popular first, endless scroll, no filters. */
internal class WinterSaleListViewModel(
    savedStateHandle: SavedStateHandle,
    private val hubs: WinterSaleHubRepository,
    region: RegionRepository,
    settings: SettingsRepository,
    private val analytics: Analytics,
    private val logger: Logger,
) : ViewModel() {
    private val shelf = WinterSaleShelf.fromKey(savedStateHandle.get<String>("shelf").orEmpty())
    private val mutableState = MutableStateFlow(initialState(savedStateHandle.get<Long>("saleEndEpochMillis")))
    val state: StateFlow<WinterSaleListState> = mutableState

    private data class Context(val country: String, val includeAdult: Boolean)

    private var context: Context? = null
    private var nextOffset: Int? = 0
    private var emptyPagesInARow = 0
    private var load: Job? = null

    init {
        if (shelf != null) {
            viewModelScope.launch {
                combine(region.observeSelectedCountry(), settings.observeMatureOptIn()) { country, adult -> Context(country.code, adult) }
                    .collect { next ->
                        if (next == context) return@collect
                        context = next
                        reload()
                    }
            }
        }
    }

    private fun initialState(saleEnd: Long?): WinterSaleListState {
        val store = (shelf as? WinterSaleShelf.Store)?.let { hubs.cachedStore(it.shopId) }
        return WinterSaleListState(
            shelf = shelf,
            saleEndEpochMillis = saleEnd,
            storeName = store?.name,
            storeDeals = store?.dealCount,
            loading = shelf != null,
            failed = shelf == null,
        )
    }

    fun loadMore() {
        val current = mutableState.value
        if (current.loading || current.loadingMore || current.endReached || current.failed || current.moreFailed) return
        fetch(first = false)
    }

    fun retry() {
        if (mutableState.value.failed) reload() else {
            mutableState.update { it.copy(moreFailed = false) }
            fetch(first = false)
        }
    }

    fun onGamePeeked() {
        val key = shelf?.key ?: return
        analytics.capture(AnalyticsEvents.WINTER_SALE_GAME_PEEKED, mapOf("shelf" to key, "from" to "list"))
    }

    private fun reload() {
        load?.cancel()
        nextOffset = 0
        emptyPagesInARow = 0
        mutableState.update {
            it.copy(deals = persistentListOf(), loading = true, loadingMore = false, failed = false, moreFailed = false, endReached = false)
        }
        fetch(first = true)
    }

    private fun fetch(first: Boolean) {
        val target = context ?: return
        val shelf = shelf ?: return
        val offset = nextOffset ?: return
        if (!first) mutableState.update { it.copy(loadingMore = true) }
        load = viewModelScope.launch {
            val page = try {
                hubs.page(shelf, target.country, target.includeAdult, offset)
            } catch (error: CancellationException) {
                throw error
            } catch (t: Throwable) {
                error(logger, t)
                null
            }
            if (target != context) return@launch
            if (page == null) {
                mutableState.update {
                    if (first) it.copy(loading = false, failed = true) else it.copy(loadingMore = false, moreFailed = true)
                }
                return@launch
            }
            nextOffset = page.nextOffset
            // ITAD's pages can overlap when its ranking shifts between calls; keep each game once.
            val seen = mutableState.value.deals.mapTo(HashSet()) { it.gameId }
            val added = page.deals.filter { seen.add(it.gameId) }
            emptyPagesInARow = if (added.isEmpty()) emptyPagesInARow + 1 else 0
            // A page that adds nothing (all repeats, or emptied by the adult filter) leaves the list as it was, so the screen
            // won't ask again on its own: ask for the next page here. Three such pages in a row end the list.
            val askAgain = added.isEmpty() && page.nextOffset != null && emptyPagesInARow < MAX_EMPTY_PAGES_IN_A_ROW
            mutableState.update { state ->
                state.copy(
                    deals = (state.deals + added).toImmutableList(),
                    storeName = state.storeName ?: page.deals.firstOrNull()?.shopName,
                    loading = askAgain && first,
                    loadingMore = askAgain && !first,
                    endReached = page.nextOffset == null || (added.isEmpty() && !askAgain),
                )
            }
            if (askAgain) fetch(first)
        }
    }

    private companion object { const val MAX_EMPTY_PAGES_IN_A_ROW = 3 }
}
