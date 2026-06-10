package pm.bam.gamedeals.feature.home.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pm.bam.gamedeals.common.ui.deal.DealBottomSheetData
import pm.bam.gamedeals.common.ui.deal.DealDetailsController
import pm.bam.gamedeals.common.ui.share.DealShareTextBuilder
import pm.bam.gamedeals.domain.models.AuthState
import pm.bam.gamedeals.domain.models.Bundle
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.DealsQuery
import pm.bam.gamedeals.domain.models.DealsSort
import pm.bam.gamedeals.domain.models.Giveaway
import pm.bam.gamedeals.domain.models.RankedGame
import pm.bam.gamedeals.domain.models.Release
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.repositories.account.AccountRepository
import pm.bam.gamedeals.domain.repositories.bundles.BundlesRepository
import pm.bam.gamedeals.domain.repositories.collection.CollectionRepository
import pm.bam.gamedeals.domain.repositories.deals.DealsRepository
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.giveaway.GiveawaysRepository
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
import pm.bam.gamedeals.domain.repositories.releases.ReleasesRepository
import pm.bam.gamedeals.domain.repositories.stats.StatsRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistToggleResult
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.fatal
import pm.bam.gamedeals.logging.info

internal const val LIMIT_HERO = 6
internal const val LIMIT_TRENDING = 10
internal const val LIMIT_STATS = 10
internal const val LIMIT_GIVEAWAYS = 5
internal const val LIMIT_BUNDLES = 5
internal const val LIMIT_RELEASES = 5
internal const val EXPIRED_STATUS = "Expired"

/**
 * Drives the curated Home feed (epic #219, Phase 5). The feed is a fixed set of independent sections
 * (account stat cards → Featured hero → Trending → Most Waitlisted → Most Collected → New Releases →
 * Bundles → Giveaways), each loaded **best-effort in parallel**: a section that fails is logged and
 * left empty (hidden) rather than sinking the whole feed. The whole feed reloads on region change.
 *
 * The account stat cards are gated on auth state (null when logged out). Row taps funnel into the
 * shared [DealDetailsController] → `DealBottomSheet`; the heart is login-gated via [WaitlistRepository].
 */
internal class HomeViewModel(
    private val storesRepository: StoresRepository,
    private val dealsRepository: DealsRepository,
    private val gamesRepository: GamesRepository,
    private val releasesRepository: ReleasesRepository,
    private val giveawaysRepository: GiveawaysRepository,
    private val bundlesRepository: BundlesRepository,
    private val statsRepository: StatsRepository,
    private val accountRepository: AccountRepository,
    private val waitlistRepository: WaitlistRepository,
    private val collectionRepository: CollectionRepository,
    private val dealShareTextBuilder: DealShareTextBuilder,
    private val regionRepository: RegionRepository,
    private val logger: Logger,
) : ViewModel() {

    val uiState: StateFlow<HomeScreenData>
        field = MutableStateFlow(HomeScreenData())

    val waitlistIds: StateFlow<ImmutableSet<String>> = waitlistRepository.observeWaitlistIds()
        .onStart { emit(persistentSetOf()) }
        .catch { emit(persistentSetOf()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), persistentSetOf())

    /** Stores keyed by id, for resolving each feed deal's store name/icon in the UI (UI Improvements #248). */
    val stores: StateFlow<ImmutableMap<Int, Store>> = storesRepository.observeStores()
        .map { list -> list.associateBy { it.storeID }.toImmutableMap() }
        .onStart { emit(persistentMapOf()) }
        .catch { emit(persistentMapOf()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), persistentMapOf())

    private val dealDetailsController = DealDetailsController(dealsRepository, storesRepository, logger)
    val dealDetails: StateFlow<DealBottomSheetData?> = dealDetailsController.dealDetails

    val events: SharedFlow<HomeUiEvent>
        field = MutableSharedFlow<HomeUiEvent>(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    private var loadJob: Job? = null

    init {
        // Initial load, then reload whenever the selected region changes. Home reads deals via the
        // un-cached getDeals passthrough, so the reload always fetches the new region's prices (#212).
        viewModelScope.launch {
            regionRepository.observeSelectedCountry()
                .map { it.code }
                .distinctUntilChanged()
                .collect { load() }
        }
    }

    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            uiState.update { it.copy(status = HomeScreenStatus.LOADING) }
            val data = coroutineScope {
                val accountStats = async { runCatching { loadAccountStats() }.getOrNull() }
                val featuredHero = async { section { dealsRepository.getDeals(DealsQuery(sort = DealsSort.TopDiscount, limit = LIMIT_HERO)) } }
                val trending = async { section { dealsRepository.getDeals(DealsQuery(sort = DealsSort.RecentlyAdded, limit = LIMIT_TRENDING)) } }
                val mostWaitlisted = async { section { statsRepository.getMostWaitlisted(LIMIT_STATS) } }
                val mostCollected = async { section { statsRepository.getMostCollected(LIMIT_STATS) } }
                val releases = async { section { loadReleases() } }
                val bundles = async { section { bundlesRepository.getBundles().take(LIMIT_BUNDLES) } }
                val giveaways = async { section { loadGiveaways() } }

                HomeScreenData(
                    status = HomeScreenStatus.DATA,
                    accountStats = accountStats.await(),
                    featuredHero = featuredHero.await(),
                    trending = trending.await(),
                    mostWaitlisted = mostWaitlisted.await(),
                    mostCollected = mostCollected.await(),
                    releases = releases.await(),
                    bundles = bundles.await(),
                    giveaways = giveaways.await(),
                )
            }
            // Best-effort sections hide on failure; if the whole feed came back empty (e.g. fully
            // offline), surface an error so the user gets a retry instead of a blank screen.
            uiState.value = data.copy(status = if (data.hasContent) HomeScreenStatus.DATA else HomeScreenStatus.ERROR)
        }
    }

    fun retry() = load()

    fun onReleaseGame(releaseTitle: String) {
        viewModelScope.launch {
            val deal = runCatching { gamesRepository.getReleaseDeal(releaseTitle) }
                .onFailure { fatal(logger, it) { "Failed to resolve release deal for '$releaseTitle'" } }
                .getOrNull()
            if (deal == null) {
                events.tryEmit(HomeUiEvent.ReleaseUnavailable)
            } else {
                loadDealDetails(deal.dealID, deal.storeID, deal.gameID, deal.title, deal.salePriceDenominated, deal.url)
            }
        }
    }

    fun loadDealDetails(dealId: String, dealStoreId: Int, dealGameId: String, dealTitle: String, dealPriceDenominated: String, dealUrl: String) {
        dealDetailsController.load(viewModelScope, dealId, dealStoreId, dealGameId, dealTitle, dealPriceDenominated, dealUrl)
    }

    fun toggleWaitlistFromDeal(data: DealBottomSheetData.DealDetailsData) = toggleWaitlist(data.gameId)

    /** Toggle a game on/off the waitlist from an inline heart; prompts sign-in when logged out. */
    fun toggleWaitlist(gameId: String) {
        viewModelScope.launch {
            if (waitlistRepository.toggleWaitlist(gameId) == WaitlistToggleResult.NOT_LOGGED_IN) {
                events.tryEmit(HomeUiEvent.SignInRequired)
            }
        }
    }

    fun dismissDealDetails() {
        dealDetailsController.dismiss(viewModelScope)
    }

    fun onShareDealClicked(data: DealBottomSheetData) {
        val text = dealShareTextBuilder.build(
            gameTitle = data.gameName,
            salePriceDenominated = data.gameSalesPriceDenominated,
            storeName = data.store.storeName,
            dealUrl = data.dealUrl,
        )
        info(logger, tag = "deal_shared") { "dealId=${data.dealId} store=${data.store.storeName}" }
        events.tryEmit(HomeUiEvent.ShareDeal(text))
    }

    /** Account stat cards are gated on auth state: null when logged out, counts when logged in. */
    private suspend fun loadAccountStats(): AccountStats? {
        if (accountRepository.observeAuthState().first() !is AuthState.LoggedIn) return null
        return coroutineScope {
            val waitlisted = async { runCatching { waitlistRepository.getWaitlist().size }.getOrDefault(0) }
            val collected = async { runCatching { collectionRepository.getCollection().size }.getOrDefault(0) }
            AccountStats(waitlistedCount = waitlisted.await(), collectedCount = collected.await())
        }
    }

    private suspend fun loadReleases(): List<Release> {
        releasesRepository.refreshReleases()
        return releasesRepository.observeReleases().first().take(LIMIT_RELEASES)
    }

    private suspend fun loadGiveaways(): List<Giveaway> {
        giveawaysRepository.refreshGiveaways()
        return giveawaysRepository.observeGiveaways().first()
            .filter { !it.status.equals(EXPIRED_STATUS, ignoreCase = true) }
            .take(LIMIT_GIVEAWAYS)
    }

    /** Runs a section's fetch best-effort: failures are logged and yield an empty (hidden) section. */
    private suspend fun <T> section(block: suspend () -> List<T>): ImmutableList<T> =
        runCatching { block() }
            .onFailure { fatal(logger, it) { "Home section failed" } }
            .getOrElse { emptyList() }
            .toImmutableList()

    internal sealed interface HomeUiEvent {
        data class ShareDeal(val text: String) : HomeUiEvent
        data object SignInRequired : HomeUiEvent
        data object ReleaseUnavailable : HomeUiEvent
    }

    @Immutable
    internal data class HomeScreenData(
        val status: HomeScreenStatus = HomeScreenStatus.LOADING,
        val accountStats: AccountStats? = null,
        val featuredHero: ImmutableList<Deal> = persistentListOf(),
        val trending: ImmutableList<Deal> = persistentListOf(),
        val mostWaitlisted: ImmutableList<RankedGame> = persistentListOf(),
        val mostCollected: ImmutableList<RankedGame> = persistentListOf(),
        val releases: ImmutableList<Release> = persistentListOf(),
        val bundles: ImmutableList<Bundle> = persistentListOf(),
        val giveaways: ImmutableList<Giveaway> = persistentListOf(),
    ) {
        val hasContent: Boolean
            get() = accountStats != null || featuredHero.isNotEmpty() || trending.isNotEmpty() ||
                mostWaitlisted.isNotEmpty() || mostCollected.isNotEmpty() || releases.isNotEmpty() ||
                bundles.isNotEmpty() || giveaways.isNotEmpty()
    }

    @Immutable
    internal data class AccountStats(
        val waitlistedCount: Int,
        val collectedCount: Int,
    )

    internal enum class HomeScreenStatus {
        LOADING, ERROR, DATA
    }
}
