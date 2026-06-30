package pm.bam.gamedeals.feature.account.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pm.bam.gamedeals.common.ui.share.DealShareTextBuilder
import pm.bam.gamedeals.domain.models.WaitlistDisplayItem
import pm.bam.gamedeals.domain.models.WaitlistDisplaySnapshot
import pm.bam.gamedeals.domain.models.thumbnail
import pm.bam.gamedeals.domain.repositories.collection.CollectionRepository
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.ignored.IgnoredRepository
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.error

/** The available sort orders for the Waitlist "buy-decision dashboard". */
internal enum class WaitlistSort {
    RECENTLY_ADDED, BIGGEST_DISCOUNT, PRICE_LOW_HIGH, NOW_AT_LOWEST;

    fun comparator(): Comparator<WaitlistRowUi> = when (this) {
        RECENTLY_ADDED -> compareByDescending<WaitlistRowUi> { it.addedEpochMs ?: Long.MIN_VALUE }.thenBy { it.title.lowercase() }
        BIGGEST_DISCOUNT -> compareByDescending<WaitlistRowUi> { it.discountPercent }.thenBy { it.title.lowercase() }
        // No-deal rows (null price) sort last via MAX_VALUE, then ascending price, then title.
        PRICE_LOW_HIGH -> compareBy<WaitlistRowUi> { it.bestPriceValue ?: Double.MAX_VALUE }.thenBy { it.title.lowercase() }
        NOW_AT_LOWEST -> compareByDescending<WaitlistRowUi> { it.isAtHistoricalLow }
            .thenByDescending { it.discountPercent }
            .thenBy { it.title.lowercase() }
    }
}

@Immutable
internal data class WaitlistRowUi(
    val gameId: String,
    val title: String,
    val imageUrl: String?,
    val addedEpochMs: Long?,
    val salePrice: String?,
    val regularPrice: String?,
    val discountPercent: Int,
    val storeName: String?,
    val storeIconUrl: String?,
    val hasVoucher: Boolean,
    val isNewHistoricalLow: Boolean,
    val isStoreLow: Boolean,
    val isAtHistoricalLow: Boolean,
    /** Raw best price for price sorting; null when there is no (shown) deal. */
    val bestPriceValue: Double?,
)

@Immutable
internal data class WaitlistUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val refreshFailed: Boolean = false,
    val sort: WaitlistSort = WaitlistSort.RECENTLY_ADDED,
    val rows: ImmutableList<WaitlistRowUi> = persistentListOf(),
)

/**
 * Backs the Waitlist sub-screen as a buy-decision dashboard. The enriched + priced snapshot
 * ([WaitlistRepository.observeWaitlistDisplay]) renders instantly (cache-first), filtered live by the
 * auth-gated id set so an un-waitlist removal vanishes with no refetch. A refresh runs on open and on region
 * change (prices are region-specific — a stale region suppresses prices until the refresh lands). Store icons
 * are resolved by shop name from [StoresRepository]. Sorting is purely in-memory over the cached list.
 */
internal class WaitlistListViewModel(
    private val waitlistRepository: WaitlistRepository,
    private val storesRepository: StoresRepository,
    private val regionRepository: RegionRepository,
    gamesRepository: GamesRepository,
    collectionRepository: CollectionRepository,
    ignoredRepository: IgnoredRepository,
    dealShareTextBuilder: DealShareTextBuilder,
    private val logger: Logger,
) : ViewModel() {

    /** Tapping a row opens the shared game-centric peek sheet (same as Home/Deals/Discover). */
    val peek = GamePeekDelegate(
        viewModelScope, gamesRepository, storesRepository, waitlistRepository,
        collectionRepository, ignoredRepository, dealShareTextBuilder, logger,
    )

    val uiState: StateFlow<WaitlistUiState>
        field = MutableStateFlow(WaitlistUiState(loading = true))

    private val sort = MutableStateFlow(WaitlistSort.RECENTLY_ADDED)

    // viewModelScope launches on the Main dispatcher (single-threaded), so a plain flag suffices.
    private var refreshInFlight = false

    init {
        refresh()
        // Re-price when the user changes region (the first emission is the current region — skip it).
        viewModelScope.launch {
            regionRepository.observeSelectedCountry().drop(1).collect { refresh() }
        }
        viewModelScope.launch {
            val storeIcons = storesRepository.observeStores().map { stores -> stores.associate { it.storeName to it.iconUrl } }
            combine(
                waitlistRepository.observeWaitlistDisplay(),
                waitlistRepository.observeWaitlistIds(),
                sort,
                regionRepository.observeSelectedCountry(),
                storeIcons,
            ) { snapshot, ids, sortOrder, country, icons ->
                Content(snapshot, ids.toSet(), sortOrder, country.code, icons)
            }.collect { content ->
                // A game added elsewhere (e.g. the peek sheet) shows up in the id set before the snapshot —
                // pull a fresh snapshot once so its row (and price) appears.
                if (content.snapshot != null && !content.snapshotCoversIds()) refresh()
                uiState.update {
                    it.copy(
                        loading = content.ids.isNotEmpty() && content.snapshot == null,
                        sort = content.sort,
                        rows = content.rows(),
                    )
                }
            }
        }
    }

    fun setSort(value: WaitlistSort) {
        sort.value = value
    }

    private fun refresh() {
        if (refreshInFlight) return
        refreshInFlight = true
        viewModelScope.launch {
            uiState.update { it.copy(refreshing = true) }
            val failed = runCatching { waitlistRepository.refreshWaitlistDisplay() }
                .onFailure { error(logger, it) }
                .isFailure
            // On success the snapshot flow drives `loading` false via combine; on a failed *cold* load
            // (no cache) nothing re-emits, so clear the spinner here to avoid it spinning forever.
            uiState.update { it.copy(refreshing = false, refreshFailed = failed, loading = if (failed) false else it.loading) }
            refreshInFlight = false
        }
    }

    private data class Content(
        val snapshot: WaitlistDisplaySnapshot?,
        val ids: Set<String>,
        val sort: WaitlistSort,
        val regionCode: String,
        val icons: Map<String, String>,
    ) {
        fun snapshotCoversIds(): Boolean =
            snapshot != null && snapshot.items.map { it.gameId }.toSet().containsAll(ids)

        fun rows(): ImmutableList<WaitlistRowUi> {
            if (ids.isEmpty() || snapshot == null) return persistentListOf()
            val suppressPrices = snapshot.regionCode != regionCode
            return snapshot.items
                .filter { it.gameId in ids }
                .map { it.toRowUi(icons, suppressPrices) }
                .sortedWith(sort.comparator())
                .toImmutableList()
        }
    }
}

private fun WaitlistDisplayItem.toRowUi(iconsByStore: Map<String, String>, suppressPrices: Boolean): WaitlistRowUi {
    val showPrice = hasDeal && !suppressPrices
    return WaitlistRowUi(
        gameId = gameId,
        title = title,
        imageUrl = artwork.thumbnail,
        addedEpochMs = addedEpochMs,
        salePrice = if (showPrice) bestPriceDenominated else null,
        regularPrice = if (showPrice) bestRegularDenominated else null,
        discountPercent = if (showPrice) (discountPercent ?: 0) else 0,
        storeName = if (showPrice) bestShopName else null,
        storeIconUrl = if (showPrice) bestShopName?.let { iconsByStore[it]?.takeIf(String::isNotBlank) } else null,
        hasVoucher = showPrice && hasVoucher,
        isNewHistoricalLow = showPrice && isNewHistoricalLow,
        isStoreLow = showPrice && isStoreLow,
        isAtHistoricalLow = showPrice && isAtHistoricalLow,
        bestPriceValue = if (showPrice) bestPriceValue else null,
    )
}
