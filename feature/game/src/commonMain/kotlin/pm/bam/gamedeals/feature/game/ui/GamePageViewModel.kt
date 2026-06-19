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
import pm.bam.gamedeals.domain.models.IgdbGame
import pm.bam.gamedeals.domain.models.PriceHistory
import pm.bam.gamedeals.domain.models.PriceWatch
import pm.bam.gamedeals.domain.models.RegionalPrice
import pm.bam.gamedeals.domain.models.RepoUpdateResult
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.repositories.collection.CollectionRepository
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.igdb.IgdbRepository
import pm.bam.gamedeals.domain.repositories.ignored.IgnoredRepository
import pm.bam.gamedeals.domain.repositories.notes.NotesRepository
import pm.bam.gamedeals.domain.repositories.pricewatch.PriceWatchRepository
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
    private val priceWatchRepository: PriceWatchRepository,
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

    /** The user's target-price alert for this game, or null when none is set (Phase 3). */
    val priceWatch: StateFlow<PriceWatch?> = gameIdFlow
        .flatMapLatest { id -> if (id == null) flowOf(null) else priceWatchRepository.observeWatch(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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

        var gameId: String? = gameIdArg
        var gameDetails: GameDetails? = null
        var igdbGame: IgdbGame? = null
        var resolvedByTitle = false

        if (gameIdArg != null) {
            // Deal entry: ITAD details anchor the page; IGDB is enrichment off the resolved Steam-appid/title.
            gameDetails = fetchOrNull { gamesRepository.getGameDetails(gameIdArg) }
            val steamAppId = gameDetails?.info?.steamAppID
            val lookupTitle = titleArg ?: gameDetails?.info?.title
            igdbGame = (steamAppId?.let { fetchOrNull { igdbRepository.fetchGameDetailsBySteamId(it) } })
                ?: lookupTitle?.let { t -> fetchOrNull { igdbRepository.fetchGameDetailsByTitle(t) } }?.also { resolvedByTitle = true }
        } else {
            // Metadata entry: IGDB anchors; resolve the ITAD id from the Steam-appid so deals can load.
            igdbGame = when {
                igdbGameIdArg != null -> fetchOrNull { igdbRepository.fetchGameDetailsByIgdbId(igdbGameIdArg) }
                steamAppIdArg != null ->
                    fetchOrNull { igdbRepository.fetchGameDetailsBySteamId(steamAppIdArg) }
                        ?: titleArg?.let { t -> fetchOrNull { igdbRepository.fetchGameDetailsByTitle(t) } }?.also { resolvedByTitle = true }
                titleArg != null -> fetchOrNull { igdbRepository.fetchGameDetailsByTitle(titleArg) }?.also { resolvedByTitle = true }
                else -> null
            }
            val steamForLookup = igdbGame?.steamAppId ?: steamAppIdArg
            val lookupTitle = titleArg ?: igdbGame?.name
            gameId = if (steamForLookup != null && lookupTitle != null) {
                fetchOrNull { gamesRepository.findGameIdBySteamAppId(steamForLookup, lookupTitle) }
            } else null
            gameDetails = gameId?.let { id -> fetchOrNull { gamesRepository.getGameDetails(id) } }
        }

        if (gameDetails == null && igdbGame == null) {
            val displayTitle = titleArg
            emit(if (displayTitle != null) GamePageData.NoMatch(displayTitle) else GamePageData.Error)
            return@flow
        }

        // Publish the resolved ITAD id so waitlist/ignore/note observe it (no-op when null).
        gameIdFlow.value = gameId

        val dealDetails = gameDetails?.deals
            ?.map { deal -> StoreDealPair(store = storesRepository.getStore(deal.storeID), deal = deal) }
            ?.toImmutableList()
            ?: persistentListOf()
        val priceHistory = gameId?.let { id -> fetchOrNull { gamesRepository.getPriceHistory(id) } }
            ?: PriceHistory(gameID = gameId.orEmpty(), points = persistentListOf())
        val gameMeta = gameId?.let { id -> fetchOrNull { gamesRepository.getGameMeta(id) } }
        val bundles = (gameId?.let { id -> fetchOrNull { gamesRepository.getBundlesForGame(id) } } ?: emptyList()).toImmutableList()
        // HowLongToBeat is a separate IGDB endpoint; merge it onto the game (best-effort).
        val enrichedIgdb = igdbGame?.let { g -> g.copy(timeToBeat = fetchOrNull { igdbRepository.fetchTimeToBeat(g.id) }) }
        val websites = enrichedIgdb?.websites?.map { it.toUi() }?.toImmutableList() ?: persistentListOf()

        emit(
            GamePageData.Data(
                title = gameDetails?.info?.title ?: enrichedIgdb?.name ?: titleArg.orEmpty(),
                gameDetails = gameDetails,
                dealDetails = dealDetails,
                priceHistory = priceHistory,
                gameMeta = gameMeta,
                bundles = bundles,
                igdbGame = enrichedIgdb,
                websites = websites,
                resolvedByTitle = resolvedByTitle,
            )
        )
    }.catch { emit(GamePageData.Error) }

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

    /** Set a target-price alert at [targetPriceValue] (Phase 3). Requires a resolved ITAD game id + deals. */
    fun setPriceWatch(targetPriceValue: Double, targetPriceDenominated: String) {
        val data = uiState.value as? GamePageData.Data ?: return
        val id = gameIdFlow.value ?: return
        viewModelScope.launch {
            priceWatchRepository.setWatch(id, data.title, targetPriceValue, targetPriceDenominated)
        }
    }

    fun removePriceWatch() {
        val id = gameIdFlow.value ?: return
        viewModelScope.launch { priceWatchRepository.removeWatch(id) }
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
        if (current.igdbGame?.id == igdbGameId) {
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
                    igdbGame = swapped,
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
            /** ITAD deal side — null when the game isn't sold anywhere we matched (metadata-only page). */
            val gameDetails: GameDetails? = null,
            val dealDetails: ImmutableList<StoreDealPair> = persistentListOf(),
            val priceHistory: PriceHistory = PriceHistory(gameID = "", points = persistentListOf()),
            val gameMeta: GameMeta? = null,
            val bundles: ImmutableList<Bundle> = persistentListOf(),
            /** IGDB metadata side — null when no IGDB record matched (deals-only page). */
            val igdbGame: IgdbGame? = null,
            val websites: ImmutableList<WebsiteUiModel> = persistentListOf(),
            val resolvedByTitle: Boolean = false,
            val candidatesState: CandidatesState = CandidatesState.Idle,
            val showPicker: Boolean = false,
            val regionalPricesState: RegionalPricesState = RegionalPricesState.Idle,
        ) : GamePageData()
    }
}
