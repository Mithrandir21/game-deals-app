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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pm.bam.gamedeals.common.ui.deal.GamePeekController
import pm.bam.gamedeals.common.ui.deal.GamePeekSheetData
import pm.bam.gamedeals.common.ui.share.DealShareTextBuilder
import pm.bam.gamedeals.domain.models.AuthState
import pm.bam.gamedeals.domain.models.Bundle
import pm.bam.gamedeals.domain.models.BundleGamePrice
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.DealsQuery
import pm.bam.gamedeals.domain.models.IgdbGame
import pm.bam.gamedeals.domain.models.DealsSortDirection
import pm.bam.gamedeals.domain.models.DealsSortField
import pm.bam.gamedeals.domain.models.RankedGame
import pm.bam.gamedeals.domain.models.RecentlyViewedGame
import pm.bam.gamedeals.domain.models.Release
import pm.bam.gamedeals.domain.models.RepoUpdateResult
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.repositories.account.AccountRepository
import pm.bam.gamedeals.domain.repositories.bundles.BundlesRepository
import pm.bam.gamedeals.domain.repositories.collection.CollectionRepository
import pm.bam.gamedeals.domain.repositories.deals.DealsRepository
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.igdb.IgdbRepository
import pm.bam.gamedeals.domain.repositories.ignored.IgnoredRepository
import pm.bam.gamedeals.domain.repositories.recentlyviewed.RecentlyViewedRepository
import pm.bam.gamedeals.domain.repositories.recommendations.RecommendationsRepository
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
import pm.bam.gamedeals.domain.repositories.releases.ReleasesRepository
import pm.bam.gamedeals.domain.repositories.stats.StatsRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.error
import pm.bam.gamedeals.logging.info

internal const val LIMIT_HERO = 6
internal const val LIMIT_TRENDING = 10
internal const val LIMIT_STATS = 10
internal const val LIMIT_BUNDLES = 5
internal const val LIMIT_RELEASES = 5
internal const val LIMIT_RECOMMENDATIONS = 12

/**
 * Drives the curated Home feed (epic #219, Phase 5). The feed is a fixed set of sections
 * (account stat cards → Featured hero + Trending → Most Waitlisted → Most Collected → New Releases →
 * Bundles), each loaded **best-effort in parallel**: a section that fails is logged and left empty
 * (hidden) rather than sinking the whole feed. The whole feed reloads on region change.
 *
 * The Featured hero and Trending grid are two visual slices of a **single** hottest-deals fetch
 * (`/deals/v2?sort=-hot`) — the hero takes the top [LIMIT_HERO], Trending the remainder — mirroring how
 * ITAD's own home promotes the top of one hot list rather than running two differently-sorted queries.
 *
 * The account stat cards are gated on auth state (null when logged out) and tap through to the Waitlist
 * / Collection lists. Every game/deal row opens the game-centric quick-peek sheet via [GamePeekController]
 * (row-consolidation work); the heart is login-gated via [WaitlistRepository]. Giveaways moved off Home
 * to their own tab, so Home is now 100% in-app.
 */
internal class HomeViewModel(
    private val storesRepository: StoresRepository,
    private val dealsRepository: DealsRepository,
    private val releasesRepository: ReleasesRepository,
    private val bundlesRepository: BundlesRepository,
    private val statsRepository: StatsRepository,
    private val accountRepository: AccountRepository,
    private val waitlistRepository: WaitlistRepository,
    private val collectionRepository: CollectionRepository,
    private val dealShareTextBuilder: DealShareTextBuilder,
    private val regionRepository: RegionRepository,
    private val ignoredRepository: IgnoredRepository,
    private val gamesRepository: GamesRepository,
    private val igdbRepository: IgdbRepository,
    private val recommendationsRepository: RecommendationsRepository,
    private val recentlyViewedRepository: RecentlyViewedRepository,
    private val logger: Logger,
) : ViewModel() {

    val uiState: StateFlow<HomeScreenData>
        field = MutableStateFlow(HomeScreenData())

    val waitlistIds: StateFlow<ImmutableSet<String>> = waitlistRepository.observeWaitlistIds()
        .onStart { emit(persistentSetOf()) }
        .catch { emit(persistentSetOf()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), persistentSetOf())

    val collectionIds: StateFlow<ImmutableSet<String>> = collectionRepository.observeCollectionIds()
        .onStart { emit(persistentSetOf()) }
        .catch { emit(persistentSetOf()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), persistentSetOf())

    /** Games on the user's ignore list — drives the deal sheet's ignore toggle (#280). */
    val ignoredIds: StateFlow<ImmutableSet<String>> = ignoredRepository.observeIgnoredIds()
        .onStart { emit(persistentSetOf()) }
        .catch { emit(persistentSetOf()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), persistentSetOf())

    /**
     * The account stat cards: null when logged out, waitlist/collection counts when logged in. Derived from
     * the same auth-gated, Room-backed id sets as the badges, so the cards show/hide reactively the moment the
     * user logs in or out — regardless of which tab or login entry point triggered it (the Room sync itself is
     * owned app-wide by `applyLibraryLifecycle`).
     */
    val accountStats: StateFlow<AccountStats?> = combine(
        accountRepository.observeAuthState(),
        waitlistRepository.observeWaitlistIds(),
        collectionRepository.observeCollectionIds(),
    ) { auth, waitlist, collection ->
        if (auth is AuthState.LoggedIn) AccountStats(waitlistedCount = waitlist.size, collectedCount = collection.size) else null
    }
        .onStart { emit(null) }
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Stores keyed by id, for resolving each feed deal's store name/icon in the UI (UI Improvements #248). */
    val stores: StateFlow<ImmutableMap<Int, Store>> = storesRepository.observeStores()
        .map { list -> list.associateBy { it.storeID }.toImmutableMap() }
        .onStart { emit(persistentMapOf()) }
        .catch { emit(persistentMapOf()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), persistentMapOf())

    private val gamePeekController = GamePeekController(gamesRepository, storesRepository, logger, igdbRepository)
    val gamePeek: StateFlow<GamePeekSheetData?> = gamePeekController.data

    /** Recently-viewed games (#211) — a device-local carousel shown after Trending; empty until something is viewed. */
    val recentlyViewed: StateFlow<ImmutableList<RecentlyViewedGame>> = recentlyViewedRepository.observeRecentlyViewed()
        .onStart { emit(persistentListOf()) }
        .catch { emit(persistentListOf()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), persistentListOf())

    val events: SharedFlow<HomeUiEvent>
        field = MutableSharedFlow<HomeUiEvent>(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    private var loadJob: Job? = null

    init {
        // Initial load, then reload whenever the selected region changes. Home reads deals via the
        // un-cached getDeals passthrough, so the reload always fetches the new region's prices.
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
                val hotDeals = async { section { dealsRepository.getDeals(DealsQuery(sortField = DealsSortField.Hottest, sortDirection = DealsSortDirection.Descending, limit = LIMIT_HERO + LIMIT_TRENDING)) } }
                val mostWaitlisted = async { section { statsRepository.getMostWaitlisted(LIMIT_STATS) } }
                val mostCollected = async { section { statsRepository.getMostCollected(LIMIT_STATS) } }
                val releases = async { section { loadReleases() } }
                val bundles = async { section { bundlesRepository.getBundles().take(LIMIT_BUNDLES) } }
                val recommendations = async { section { recommendationsRepository.getRecommendations(LIMIT_RECOMMENDATIONS) } }

                // Enrich the ranked rows with a current price + discount (one batched lookup over both
                // lists' game ids) so they read with the same anatomy as the Trending deal rows.
                val mostWaitlistedRaw = mostWaitlisted.await()
                val mostCollectedRaw = mostCollected.await()
                val prices = loadGamePrices(mostWaitlistedRaw + mostCollectedRaw)

                val hot = hotDeals.await()
                HomeScreenData(
                    status = HomeScreenStatus.DATA,
                    featuredHero = hot.take(LIMIT_HERO).toImmutableList(),
                    trending = hot.drop(LIMIT_HERO).toImmutableList(),
                    mostWaitlisted = enrichRanked(mostWaitlistedRaw, prices),
                    mostCollected = enrichRanked(mostCollectedRaw, prices),
                    releases = releases.await(),
                    bundles = bundles.await(),
                    recommendations = recommendations.await(),
                )
            }
            // Best-effort sections hide on failure; if the whole feed came back empty (e.g. fully
            // offline), surface an error so the user gets a retry instead of a blank screen.
            uiState.value = data.copy(status = if (data.hasContent) HomeScreenStatus.DATA else HomeScreenStatus.ERROR)
        }
    }

    fun retry() = load()

    /** Open the game-centric peek sheet for a game/deal row (hero, trending, ranked). */
    fun peekGame(gameId: String, gameName: String, thumb: String?) =
        gamePeekController.load(viewModelScope, gameId, gameName, thumb)

    /** Open the peek sheet for a "New release" row, which carries only a title (resolved on open). */
    fun peekRelease(title: String, thumb: String?) =
        gamePeekController.loadByTitle(viewModelScope, title, thumb)

    /** Toggle a game on/off the waitlist from the peek sheet; prompts sign-in when logged out. */
    fun toggleWaitlist(gameId: String) {
        viewModelScope.launch {
            if (waitlistRepository.toggleWaitlist(gameId) == RepoUpdateResult.NOT_LOGGED_IN) {
                events.tryEmit(HomeUiEvent.SignInRequired)
            }
        }
    }

    /** Toggle a game in/out of the collection from the peek sheet; prompts sign-in when logged out. */
    fun toggleCollection(gameId: String) {
        viewModelScope.launch {
            if (collectionRepository.toggleCollection(gameId) == RepoUpdateResult.NOT_LOGGED_IN) {
                events.tryEmit(HomeUiEvent.SignInRequired)
            }
        }
    }

    /** Toggle a game on/off the ignore list from the peek sheet; prompts sign-in when logged out. */
    fun toggleIgnore(gameId: String) {
        viewModelScope.launch {
            if (ignoredRepository.toggleIgnored(gameId) == RepoUpdateResult.NOT_LOGGED_IN) {
                events.tryEmit(HomeUiEvent.SignInRequired)
            }
        }
    }

    fun dismissPeek() {
        gamePeekController.dismiss(viewModelScope)
    }

    /** Remove one game from the recently-viewed history (long-press on its tile). */
    fun onRemoveRecentlyViewed(gameId: String) {
        viewModelScope.launch { recentlyViewedRepository.remove(gameId) }
    }

    /** Clear the whole recently-viewed history (carousel "Clear"). */
    fun onClearRecentlyViewed() {
        viewModelScope.launch { recentlyViewedRepository.clear() }
    }

    fun onShareClicked(data: GamePeekSheetData.Data) {
        val best = data.bestDeal ?: return
        val text = dealShareTextBuilder.build(
            gameTitle = data.gameName,
            salePriceDenominated = best.deal.priceDenominated,
            storeName = best.store.storeName,
            dealUrl = best.deal.url,
        )
        info(logger, tag = "deal_shared") { "gameId=${data.gameId} store=${best.store.storeName}" }
        events.tryEmit(HomeUiEvent.ShareDeal(text))
    }

    private suspend fun loadReleases(): List<Release> {
        releasesRepository.refreshReleases()
        return releasesRepository.observeReleases().first().take(LIMIT_RELEASES)
    }

    /** One batched best-price lookup over the ranked games' ids; best-effort (empty on failure). */
    private suspend fun loadGamePrices(games: List<RankedGame>): Map<String, BundleGamePrice> = runCatching {
        val ids = games.map { it.gameId }.distinct()
        if (ids.isEmpty()) emptyMap() else gamesRepository.getGamePrices(ids).associateBy { it.gameId }
    }.getOrElse { emptyMap() }

    /** Overlay each ranked game's current best price + discount; rows without a price keep their snapshot. */
    private fun enrichRanked(games: List<RankedGame>, prices: Map<String, BundleGamePrice>): ImmutableList<RankedGame> =
        games.map { game ->
            val price = prices[game.gameId] ?: return@map game
            game.copy(
                priceDenominated = price.bestPriceDenominated ?: game.priceDenominated,
                cutPercent = price.bestCutPercent,
                regularPriceDenominated = price.bestRegularDenominated,
                storeName = price.bestShopName,
                hasVoucher = price.bestHasVoucher,
                isNewHistoricalLow = price.bestIsNewHistoricalLow,
                isStoreLow = price.bestIsStoreLow,
            )
        }.toImmutableList()

    /** Runs a section's fetch best-effort: failures are logged and yield an empty (hidden) section. */
    private suspend fun <T> section(block: suspend () -> List<T>): ImmutableList<T> =
        runCatching { block() }
            .onFailure { error(logger, it) { "Home section failed" } }
            .getOrElse { emptyList() }
            .toImmutableList()

    internal sealed interface HomeUiEvent {
        data class ShareDeal(val text: String) : HomeUiEvent
        data object SignInRequired : HomeUiEvent
    }

    @Immutable
    internal data class HomeScreenData(
        val status: HomeScreenStatus = HomeScreenStatus.LOADING,
        val featuredHero: ImmutableList<Deal> = persistentListOf(),
        val trending: ImmutableList<Deal> = persistentListOf(),
        val mostWaitlisted: ImmutableList<RankedGame> = persistentListOf(),
        val mostCollected: ImmutableList<RankedGame> = persistentListOf(),
        val releases: ImmutableList<Release> = persistentListOf(),
        val bundles: ImmutableList<Bundle> = persistentListOf(),
        val recommendations: ImmutableList<IgdbGame.IgdbSimilarGame> = persistentListOf(),
    ) {
        // Account stats are reactive and independent (see [accountStats]); the feed's error/empty decision is
        // based purely on its content sections.
        val hasContent: Boolean
            get() = featuredHero.isNotEmpty() || trending.isNotEmpty() ||
                mostWaitlisted.isNotEmpty() || mostCollected.isNotEmpty() || releases.isNotEmpty() ||
                bundles.isNotEmpty() || recommendations.isNotEmpty()
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
