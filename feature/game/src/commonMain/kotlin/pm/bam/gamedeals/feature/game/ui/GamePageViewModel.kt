package pm.bam.gamedeals.feature.game.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pm.bam.gamedeals.common.favicon.FaviconResolver
import pm.bam.gamedeals.common.logFlow
import pm.bam.gamedeals.common.ui.share.DealShareTextBuilder
import pm.bam.gamedeals.domain.models.Bundle
import pm.bam.gamedeals.domain.models.GameDetails
import pm.bam.gamedeals.domain.models.GameMeta
import pm.bam.gamedeals.domain.models.dealQuality
import pm.bam.gamedeals.domain.models.IgdbGame
import pm.bam.gamedeals.domain.models.PriceHistory
import pm.bam.gamedeals.domain.models.RegionalPrice
import pm.bam.gamedeals.domain.models.RepoUpdateResult
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.repositories.collection.CollectionRepository
import pm.bam.gamedeals.domain.repositories.franchise.FollowedFranchiseRepository
import pm.bam.gamedeals.domain.repositories.franchise.FranchiseFollowSeeder
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.igdb.IgdbRepository
import pm.bam.gamedeals.domain.repositories.ignored.IgnoredRepository
import pm.bam.gamedeals.domain.repositories.notes.NotesRepository
import pm.bam.gamedeals.domain.repositories.recentlyviewed.RecentlyViewedRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.info

/**
 * The unified Game Page ViewModel (epic #291, Phase 4) — merges the old `GameViewModel` (ITAD deals,
 * price history, notes, waitlist/ignore) and `GameDetailsViewModel` (IGDB metadata, candidate picker)
 * into one.
 *
 * **Identity resolution (both directions).** The page is reachable from any of `{gameId, steamAppId,
 * igdbGameId, title}` and resolves the missing side via the existing Steam-appid bridge:
 *  - **Deal entry** (`gameId`): [GamesRepository.getGameDetails] is the anchor; IGDB is resolved from the
 *    game's Steam-appid (or its title) as best-effort enrichment.
 *  - **Metadata entry** (`igdbGameId` / `steamAppId` / `title`): IGDB is the anchor; the ITAD `gameId` is
 *    resolved via [GamesRepository.findGameIdBySteamAppId] so deals/price-history/meta/bundles can load.
 *
 * Every enrichment is **best-effort** — a failure hides its section, never the page (the `fetch*Safely`
 * helpers swallow non-cancellation throwables). Only the total miss (no deal side AND no IGDB side) ends
 * in [GamePageData.NoMatch] (when a title is available) or [GamePageData.Error].
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class GamePageViewModel(
    private val savedStateHandle: SavedStateHandle,
    private val logger: Logger,
    private val gamesRepository: GamesRepository,
    private val storesRepository: StoresRepository,
    private val igdbRepository: IgdbRepository,
    private val dealShareTextBuilder: DealShareTextBuilder,
    private val waitlistRepository: WaitlistRepository,
    private val collectionRepository: CollectionRepository,
    private val ignoredRepository: IgnoredRepository,
    private val notesRepository: NotesRepository,
    private val faviconResolver: FaviconResolver,
    private val followedFranchiseRepository: FollowedFranchiseRepository,
    private val franchiseFollowSeeder: FranchiseFollowSeeder,
    private val recentlyViewedRepository: RecentlyViewedRepository,
) : ViewModel() {

    // The ITAD game UUID. Seeded from the deal-entry arg and *updated* once an IGDB-only entry resolves its
    // ITAD id (so waitlist/ignore/note start working). null = no ITAD match (those actions are inert).
    private val gameIdFlow = MutableStateFlow(savedStateHandle.get<String>("gameId"))

    val isWaitlisted: StateFlow<Boolean> = gameIdFlow
        .flatMapLatest { id -> if (id == null) flowOf(false) else waitlistRepository.observeIsWaitlisted(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val isCollected: StateFlow<Boolean> = gameIdFlow
        .flatMapLatest { id -> if (id == null) flowOf(false) else collectionRepository.observeIsCollected(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val isIgnored: StateFlow<Boolean> = gameIdFlow
        .flatMapLatest { id -> if (id == null) flowOf(false) else ignoredRepository.observeIsIgnored(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val note: StateFlow<String?> = gameIdFlow
        .flatMapLatest { id -> if (id == null) flowOf(null) else notesRepository.observeNote(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Ids of the franchises/series the user follows (#7) — drives the game page's follow toggle. */
    val followedFranchiseIds: StateFlow<Set<Long>> = followedFranchiseRepository.observeFollowedIds()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val uiState: StateFlow<GamePageData>
        field = MutableStateFlow<GamePageData>(GamePageData.Loading)

    private val reloadTrigger = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val events: SharedFlow<GameUiEvent>
        field = MutableSharedFlow<GameUiEvent>(replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    init {
        viewModelScope.launch {
            reloadTrigger.onStart { emit(Unit) }
                .flatMapLatest { loadFlow() }
                .logFlow(logger)
                .collect { uiState.emit(it) }
        }
    }

    fun reload() {
        reloadTrigger.tryEmit(Unit)
    }

    private fun loadFlow(): Flow<GamePageData> = flow {
        emit(GamePageData.Loading)

        val gameIdArg = savedStateHandle.get<String>("gameId")
        val steamAppIdArg = savedStateHandle.get<Int>("steamAppId")
        val igdbGameIdArg = savedStateHandle.get<Long>("igdbGameId")
        val titleArg = savedStateHandle.get<String>("title")?.takeIf { it.isNotBlank() }

        if (gameIdArg == null && steamAppIdArg == null && igdbGameIdArg == null && titleArg == null) {
            emit(GamePageData.Error)
            return@flow
        }

        // Each facet captures its own outcome: Loaded(value | null | empty) when the fetch ran (even if it
        // returned nothing), Error when it threw — so the tabs can tell "no data" apart from "load failed".
        var gameId: String? = gameIdArg
        val dealsState: SectionState<GameDetails?>
        val igdbResolution: IgdbResolution

        if (gameIdArg != null) {
            // Deal entry: ITAD details anchor the page; IGDB is enrichment off the resolved Steam-appid/title.
            dealsState = fetchSection { gamesRepository.getGameDetails(gameIdArg) }
            igdbResolution = resolveIgdbForCurrentArgs(gameDetails = (dealsState as? SectionState.Loaded)?.value)
        } else {
            // Metadata entry: IGDB anchors; resolve the ITAD id from the Steam-appid so deals can load.
            igdbResolution = resolveIgdbForCurrentArgs(gameDetails = null)
            val igdbGame = (igdbResolution.state as? SectionState.Loaded)?.value
            val steamForLookup = igdbGame?.steamAppId ?: steamAppIdArg
            val lookupTitle = titleArg ?: igdbGame?.name
            gameId = if (steamForLookup != null && lookupTitle != null) {
                fetchOrNull { gamesRepository.findGameIdBySteamAppId(steamForLookup, lookupTitle) }
            } else null
            dealsState = if (gameId != null) fetchSection { gamesRepository.getGameDetails(gameId) } else SectionState.Loaded(null)
        }

        val gameDetails = (dealsState as? SectionState.Loaded)?.value
        val igdbGame = (igdbResolution.state as? SectionState.Loaded)?.value

        if (gameDetails == null && igdbGame == null) {
            // No usable data on either side. A thrown fetch → retryable Error; otherwise it's a genuine miss.
            val errored = dealsState is SectionState.Error || igdbResolution.state is SectionState.Error
            emit(
                when {
                    errored -> GamePageData.Error
                    titleArg != null -> GamePageData.NoMatch(titleArg)
                    else -> GamePageData.Error
                }
            )
            return@flow
        }

        // Publish the resolved ITAD id so waitlist/ignore/note observe it (no-op when null).
        gameIdFlow.value = gameId

        // Record this view for the recently-viewed carousel (#211) — only when we have an ITAD id (the
        // peek/open path needs it). Upsert de-dupes and moves the game back to the top.
        gameId?.let { id ->
            val viewedTitle = gameDetails?.info?.title ?: igdbGame?.name ?: titleArg.orEmpty()
            recentlyViewedRepository.recordView(id, viewedTitle, gameDetails?.info?.artwork?.boxart)
        }

        val dealDetails = mapDealDetails(gameDetails)
        val resolvedGameId = gameId
        val priceHistoryState: SectionState<PriceHistory> =
            if (resolvedGameId != null) fetchSection { gamesRepository.getPriceHistory(resolvedGameId) }
            else SectionState.Loaded(PriceHistory(gameID = "", points = persistentListOf()))
        val gameMetaState: SectionState<GameMeta?> =
            if (resolvedGameId != null) fetchSection { gamesRepository.getGameMeta(resolvedGameId) }
            else SectionState.Loaded(null)
        // Bundles + links stay best-effort: a failure silently hides the section (Overview is IGDB-primary).
        val bundles = (resolvedGameId?.let { id -> fetchOrNull { gamesRepository.getBundlesForGame(id) } } ?: emptyList()).toImmutableList()
        val websites = igdbGame?.websites?.map { it.toUi() }?.toImmutableList() ?: persistentListOf()

        emit(
            GamePageData.Data(
                title = gameDetails?.info?.title ?: igdbGame?.name ?: titleArg.orEmpty(),
                gameId = resolvedGameId,
                deals = dealsState,
                dealDetails = dealDetails,
                priceHistory = priceHistoryState,
                gameMeta = gameMetaState,
                bundles = bundles,
                igdb = igdbResolution.state,
                websites = websites,
                resolvedByTitle = igdbResolution.resolvedByTitle,
            )
        )
    }.catch { emit(GamePageData.Error) }

    /** Maps a game's deals onto their stores for the Prices tab (no-op when there's no deal side). */
    private suspend fun mapDealDetails(gameDetails: GameDetails?): ImmutableList<StoreDealPair> =
        gameDetails?.deals
            ?.map { deal -> StoreDealPair(store = storesRepository.getStore(deal.storeID), deal = deal) }
            ?.toImmutableList()
            ?: persistentListOf()

    /** A captured fetch outcome wrapped for the UI: [SectionState.Loaded] on success (value may be null/empty), [SectionState.Error] when it threw. */
    private suspend fun <T> fetchSection(block: suspend () -> T): SectionState<T> = try {
        SectionState.Loaded(block())
    } catch (ce: CancellationException) {
        throw ce
    } catch (_: Throwable) {
        SectionState.Error
    }

    private data class IgdbResolution(val state: SectionState<IgdbGame?>, val resolvedByTitle: Boolean)

    /**
     * Resolves (and HowLongToBeat-enriches) the IGDB side for the current navigation args, capturing the
     * outcome as a [SectionState]. Shared by the initial load and [retryIgdb]. For a deal entry the lookup
     * keys off the resolved [gameDetails] (Steam-appid then title); for a metadata entry it keys off the
     * args directly. A network failure yields [SectionState.Error]; a clean no-match yields `Loaded(null)`.
     */
    private suspend fun resolveIgdbForCurrentArgs(gameDetails: GameDetails?): IgdbResolution {
        val gameIdArg = savedStateHandle.get<String>("gameId")
        val steamAppIdArg = savedStateHandle.get<Int>("steamAppId")
        val igdbGameIdArg = savedStateHandle.get<Long>("igdbGameId")
        val titleArg = savedStateHandle.get<String>("title")?.takeIf { it.isNotBlank() }
        return resolveIgdb {
            if (gameIdArg != null) {
                val steamAppId = gameDetails?.info?.steamAppID
                val lookupTitle = titleArg ?: gameDetails?.info?.title
                val bySteam = steamAppId?.let { igdbRepository.fetchGameDetailsBySteamId(it) }
                if (bySteam != null) bySteam to false
                else lookupTitle?.let { t -> igdbRepository.fetchGameDetailsByTitle(t) }?.let { it to true } ?: (null to false)
            } else when {
                igdbGameIdArg != null -> igdbRepository.fetchGameDetailsByIgdbId(igdbGameIdArg) to false
                steamAppIdArg != null -> {
                    val bySteam = igdbRepository.fetchGameDetailsBySteamId(steamAppIdArg)
                    if (bySteam != null) bySteam to false
                    else titleArg?.let { t -> igdbRepository.fetchGameDetailsByTitle(t) }?.let { it to true } ?: (null to false)
                }
                titleArg != null -> igdbRepository.fetchGameDetailsByTitle(titleArg)?.let { it to true } ?: (null to false)
                else -> null to false
            }
        }
    }

    /** Runs an IGDB [resolve] (returning `game to resolvedByTitle`), merges HowLongToBeat, and captures Error-on-throw. */
    private suspend fun resolveIgdb(resolve: suspend () -> Pair<IgdbGame?, Boolean>): IgdbResolution = try {
        val (game, byTitle) = resolve()
        // HowLongToBeat is a separate IGDB endpoint; merge it onto the game (best-effort).
        val enriched = game?.let { g -> g.copy(timeToBeat = fetchOrNull { igdbRepository.fetchTimeToBeat(g.id) }) }
        IgdbResolution(SectionState.Loaded(enriched), resolvedByTitle = byTitle && enriched != null)
    } catch (ce: CancellationException) {
        throw ce
    } catch (_: Throwable) {
        IgdbResolution(SectionState.Error, resolvedByTitle = false)
    }

    /** Re-fetch only the deal side (Prices + History's cheapest-ever) after a failure. */
    fun retryDeals() {
        val current = uiState.value as? GamePageData.Data ?: return
        val id = gameIdFlow.value ?: return
        uiState.value = current.copy(deals = SectionState.Loading, dealDetails = persistentListOf())
        viewModelScope.launch {
            val state = fetchSection { gamesRepository.getGameDetails(id) }
            val dealDetails = mapDealDetails((state as? SectionState.Loaded)?.value)
            val after = uiState.value as? GamePageData.Data ?: return@launch
            uiState.value = after.copy(deals = state, dealDetails = dealDetails)
        }
    }

    /** Re-fetch only the price-history chart (History tab) after a failure. */
    fun retryPriceHistory() {
        val current = uiState.value as? GamePageData.Data ?: return
        val id = gameIdFlow.value ?: return
        uiState.value = current.copy(priceHistory = SectionState.Loading)
        viewModelScope.launch {
            val state = fetchSection { gamesRepository.getPriceHistory(id) }
            val after = uiState.value as? GamePageData.Data ?: return@launch
            uiState.value = after.copy(priceHistory = state)
        }
    }

    /** Re-fetch only the live-signal metadata (Stats tab) after a failure. */
    fun retryGameMeta() {
        val current = uiState.value as? GamePageData.Data ?: return
        val id = gameIdFlow.value ?: return
        uiState.value = current.copy(gameMeta = SectionState.Loading)
        viewModelScope.launch {
            val state = fetchSection { gamesRepository.getGameMeta(id) }
            val after = uiState.value as? GamePageData.Data ?: return@launch
            uiState.value = after.copy(gameMeta = state)
        }
    }

    /** Re-fetch only the IGDB metadata (Overview tab + hero) after a failure. */
    fun retryIgdb() {
        val current = uiState.value as? GamePageData.Data ?: return
        uiState.value = current.copy(igdb = SectionState.Loading)
        viewModelScope.launch {
            val resolution = resolveIgdbForCurrentArgs((current.deals as? SectionState.Loaded)?.value)
            val igdbGame = (resolution.state as? SectionState.Loaded)?.value
            val websites = igdbGame?.websites?.map { it.toUi() }?.toImmutableList() ?: persistentListOf()
            val after = uiState.value as? GamePageData.Data ?: return@launch
            uiState.value = after.copy(igdb = resolution.state, websites = websites, resolvedByTitle = resolution.resolvedByTitle)
        }
    }

    fun toggleWaitlist() {
        if (uiState.value !is GamePageData.Data) return
        val id = gameIdFlow.value ?: return
        viewModelScope.launch {
            if (waitlistRepository.toggleWaitlist(id) == RepoUpdateResult.NOT_LOGGED_IN) {
                events.tryEmit(GameUiEvent.SignInRequired)
            }
        }
    }

    fun toggleCollection() {
        if (uiState.value !is GamePageData.Data) return
        val id = gameIdFlow.value ?: return
        viewModelScope.launch {
            if (collectionRepository.toggleCollection(id) == RepoUpdateResult.NOT_LOGGED_IN) {
                events.tryEmit(GameUiEvent.SignInRequired)
            }
        }
    }

    fun toggleIgnore() {
        if (uiState.value !is GamePageData.Data) return
        val id = gameIdFlow.value ?: return
        viewModelScope.launch {
            if (ignoredRepository.toggleIgnored(id) == RepoUpdateResult.NOT_LOGGED_IN) {
                events.tryEmit(GameUiEvent.SignInRequired)
            }
        }
    }

    fun setNote(text: String) {
        val id = gameIdFlow.value ?: return
        viewModelScope.launch {
            if (notesRepository.setNote(id, text) == RepoUpdateResult.NOT_LOGGED_IN) {
                events.tryEmit(GameUiEvent.SignInRequired)
            }
        }
    }

    fun deleteNote() {
        val id = gameIdFlow.value ?: return
        viewModelScope.launch {
            if (notesRepository.deleteNote(id) == RepoUpdateResult.NOT_LOGGED_IN) {
                events.tryEmit(GameUiEvent.SignInRequired)
            }
        }
    }

    /** Follow/unfollow a franchise/series (#7). */
    fun toggleFollowFranchise(franchiseId: Long, name: String) {
        viewModelScope.launch {
            followedFranchiseRepository.toggle(franchiseId, name)
            // On a *new* follow, baseline the franchise's already-on-sale games as "seen" so the next poll
            // only alerts on future drops (no back-catalog tray flood). Best-effort, off the UI path.
            val nowFollowed = followedFranchiseRepository.getFollowed().any { it.franchiseId == franchiseId }
            if (nowFollowed) {
                runCatching { franchiseFollowSeeder.seedSeen(franchiseId) }
            }
        }
    }

    fun onShareDealClicked(gameInfo: GameDetails.GameInfo, store: Store, deal: GameDetails.GameDeal) {
        val text = dealShareTextBuilder.build(
            gameTitle = gameInfo.title,
            salePriceDenominated = deal.priceDenominated,
            storeName = store.storeName,
            dealUrl = deal.url,
        )
        info(logger, tag = "deal_shared") { "dealId=${deal.dealID} store=${store.storeName}" }
        events.tryEmit(GameUiEvent.ShareDeal(text))
    }

    // --- IGDB title-match warning + candidate picker (from the old GameDetailsViewModel) ---

    fun onWarningTap() {
        val current = uiState.value as? GamePageData.Data ?: return
        if (!current.resolvedByTitle) return
        val title = current.title.takeIf { it.isNotBlank() } ?: return

        uiState.value = current.copy(showPicker = true)
        if (current.candidatesState is CandidatesState.Loaded || current.candidatesState is CandidatesState.Loading) return

        viewModelScope.launch {
            val pre = uiState.value as? GamePageData.Data ?: return@launch
            uiState.value = pre.copy(candidatesState = CandidatesState.Loading)
            val next: CandidatesState = try {
                CandidatesState.Loaded(igdbRepository.fetchSearchCandidatesByTitle(title))
            } catch (_: Throwable) {
                CandidatesState.Error
            }
            val after = uiState.value as? GamePageData.Data ?: return@launch
            uiState.value = after.copy(candidatesState = next)
        }
    }

    fun onPickerDismiss() {
        val current = uiState.value as? GamePageData.Data ?: return
        uiState.value = current.copy(showPicker = false)
    }

    /**
     * Lazily loads the cross-region price comparison the first time the Regions tab is opened (epic #291,
     * Phase 7) — it's N per-country round-trips, so it must not run on every page open. No ITAD id (a
     * metadata-only page) → an empty Loaded state (nothing to compare).
     */
    fun onRegionsSelected() {
        val current = uiState.value as? GamePageData.Data ?: return
        if (current.regionalPricesState != RegionalPricesState.Idle) return
        val id = gameIdFlow.value
        if (id == null) {
            uiState.value = current.copy(regionalPricesState = RegionalPricesState.Loaded(persistentListOf()))
            return
        }
        uiState.value = current.copy(regionalPricesState = RegionalPricesState.Loading)
        viewModelScope.launch {
            val next: RegionalPricesState = try {
                RegionalPricesState.Loaded(gamesRepository.getRegionalPrices(id).toImmutableList())
            } catch (_: Throwable) {
                RegionalPricesState.Error
            }
            val after = uiState.value as? GamePageData.Data ?: return@launch
            uiState.value = after.copy(regionalPricesState = next)
        }
    }

    /** Swap only the IGDB metadata side to the picked match; the ITAD deal side (gameId) is unchanged. */
    fun onCandidatePicked(igdbGameId: Long) {
        val current = uiState.value as? GamePageData.Data ?: return
        if (current.igdbGameOrNull?.id == igdbGameId) {
            uiState.value = current.copy(showPicker = false)
            return
        }
        viewModelScope.launch {
            val swapped = fetchOrNull {
                igdbRepository.fetchGameDetailsByIgdbId(igdbGameId)
                    ?.let { g -> g.copy(timeToBeat = fetchOrNull { igdbRepository.fetchTimeToBeat(g.id) }) }
            }
            val base = uiState.value as? GamePageData.Data ?: return@launch
            uiState.value = if (swapped != null) {
                base.copy(
                    igdb = SectionState.Loaded(swapped),
                    websites = swapped.websites.map { it.toUi() }.toImmutableList(),
                    showPicker = false,
                )
            } else {
                base.copy(showPicker = false)
            }
        }
    }

    private suspend fun <T> fetchOrNull(block: suspend () -> T?): T? = try {
        block()
    } catch (ce: CancellationException) {
        throw ce
    } catch (_: Throwable) {
        null
    }

    private fun IgdbGame.IgdbWebsite.toUi(): WebsiteUiModel {
        val ref = faviconResolver.resolve(url)
        return WebsiteUiModel(url = url, category = category, faviconUrl = ref.url, faviconCacheKey = ref.cacheKey)
    }

    internal sealed interface GameUiEvent {
        data class ShareDeal(val text: String) : GameUiEvent
        data object SignInRequired : GameUiEvent
    }

    sealed class CandidatesState {
        data object Idle : CandidatesState()
        data object Loading : CandidatesState()
        data class Loaded(val items: ImmutableList<IgdbGame.IgdbSimilarGame>) : CandidatesState()
        data object Error : CandidatesState()
    }

    sealed class RegionalPricesState {
        data object Idle : RegionalPricesState()
        data object Loading : RegionalPricesState()
        data class Loaded(val items: ImmutableList<RegionalPrice>) : RegionalPricesState()
        data object Error : RegionalPricesState()
    }

    sealed class GamePageData {
        data object Loading : GamePageData()
        data object Error : GamePageData()
        data class NoMatch(val title: String) : GamePageData()

        @Immutable
        data class Data(
            val title: String,
            /** Resolved ITAD game id (same id space as the peek sheet's), or null on a metadata-only page. Used to tag `deal_store_opened`. */
            val gameId: String? = null,
            /**
             * ITAD deal side. `Loaded(GameDetails)` when sold somewhere we matched; `Loaded(null)` when there's
             * no ITAD match (metadata-only page); [SectionState.Error] when the fetch failed. Feeds Prices +
             * History's cheapest-ever.
             */
            val deals: SectionState<GameDetails?> = SectionState.Loaded(null),
            val dealDetails: ImmutableList<StoreDealPair> = persistentListOf(),
            val priceHistory: SectionState<PriceHistory> = SectionState.Loaded(PriceHistory(gameID = "", points = persistentListOf())),
            val gameMeta: SectionState<GameMeta?> = SectionState.Loaded(null),
            val bundles: ImmutableList<Bundle> = persistentListOf(),
            /** IGDB metadata side. `Loaded(null)` when no IGDB record matched (deals-only page); [SectionState.Error] on failure. */
            val igdb: SectionState<IgdbGame?> = SectionState.Loaded(null),
            val websites: ImmutableList<WebsiteUiModel> = persistentListOf(),
            val resolvedByTitle: Boolean = false,
            val candidatesState: CandidatesState = CandidatesState.Idle,
            val showPicker: Boolean = false,
            val regionalPricesState: RegionalPricesState = RegionalPricesState.Idle,
        ) : GamePageData() {
            /** The loaded ITAD details, or null when absent/loading/errored — for callers that only need the value. */
            val gameDetailsOrNull: GameDetails? get() = (deals as? SectionState.Loaded)?.value
            /** The loaded live-signal metadata, or null when absent/loading/errored. */
            val gameMetaOrNull: GameMeta? get() = (gameMeta as? SectionState.Loaded)?.value
            /** The loaded IGDB record, or null when absent/loading/errored — for the hero/picker. */
            val igdbGameOrNull: IgdbGame? get() = (igdb as? SectionState.Loaded)?.value

            /**
             * The hero "buy box": the cheapest current deal (with its store) plus the [dealQuality] buy-signal,
             * or null when there's no live deal. Derived (no extra fetch), so the hero and the condensed sticky
             * bar always agree, and it auto-tracks [retryDeals] since it reads [dealDetails]/[deals].
             */
            val buyBox: BuyBoxState? get() {
                val pair = dealDetails.minByOrNull { it.deal.priceValue } ?: return null
                return BuyBoxState(pair = pair, quality = gameDetailsOrNull?.dealQuality())
            }
        }
    }
}
