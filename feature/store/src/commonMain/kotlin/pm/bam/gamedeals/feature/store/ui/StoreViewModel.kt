package pm.bam.gamedeals.feature.store.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pm.bam.gamedeals.common.logFlow
import pm.bam.gamedeals.common.ui.deal.GamePeekController
import pm.bam.gamedeals.common.ui.deal.GamePeekSheetData
import pm.bam.gamedeals.common.ui.share.DealShareTextBuilder
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.RepoUpdateResult
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.repositories.deals.DealsRepository
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.ignored.IgnoredRepository
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
import pm.bam.gamedeals.domain.repositories.collection.CollectionRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.info

internal class StoreViewModel(
    savedStateHandle: SavedStateHandle,
    private val logger: Logger,
    private val dealsRepository: DealsRepository,
    private val storesRepository: StoresRepository,
    private val gamesRepository: GamesRepository,
    private val dealShareTextBuilder: DealShareTextBuilder,
    private val waitlistRepository: WaitlistRepository,
    private val collectionRepository: CollectionRepository,
    private val regionRepository: RegionRepository,
    private val ignoredRepository: IgnoredRepository,
) : ViewModel() {

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

    // We store and react to the StoreId changes so that only a single 'deals' flow can exists.
    private val storeIdFlow = MutableStateFlow(savedStateHandle.get<Int>("storeId"))

    // Retry counter: every increment re-runs the store-details load and re-subscribes the
    // hot 'observeStoreDeals' source. `combine` fires whenever either upstream emits, and
    // `flatMapLatest` cancels the in-flight inner flow before relaunching it.
    private val retryTrigger = MutableStateFlow(0)

    // The shared game-centric peek sheet (same as Home/Deals/Discover). A deal row carries the
    // ITAD gameId + title + thumb; the controller fetches the best deal + other stores itself.
    private val gamePeekController = GamePeekController(gamesRepository, storesRepository, logger)
    val gamePeek: StateFlow<GamePeekSheetData?> = gamePeekController.data

    val events: SharedFlow<StoreUiEvent>
        field = MutableSharedFlow<StoreUiEvent>(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<StoreScreenData> = combine(storeIdFlow, retryTrigger) { id, _ -> id }
        .flatMapLatest { id: Int? ->
            when (id) {
                null -> flowOf<StoreScreenData>(StoreScreenData.Error)
                else -> flowOf(id)
                    .map { storesRepository.getStore(id) }
                    .map<Store, StoreScreenData> { StoreScreenData.Data(it) }
                    .logFlow(logger)
                    .catch { emit(StoreScreenData.Error) }
                    .onStart { emit(StoreScreenData.Loading) }
            }
        }
        .logFlow(logger)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = StoreScreenData.Loading
        )

    @OptIn(ExperimentalCoroutinesApi::class)
    val deals: StateFlow<ImmutableList<Deal>> = combine(storeIdFlow, retryTrigger) { id, attempt -> id to attempt }
        .filter { (id, _) -> id != null } // Skip our initial null value
        .distinctUntilChanged() // Skip refetching if neither the storeId nor the retry counter changed (e.g. orientation change)
        .flatMapLatest { (id, _) -> dealsRepository.observeStoreDeals(id!!) }
        .map { it.toImmutableList() }
        .logFlow(logger)
        .catch { emit(persistentListOf()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = persistentListOf()
        )

    init {
        // Re-fetch this store's deals when the region changes. observeStoreDeals reads the active
        // region's cached rows (D5 / Phase 2), so re-subscribing reads the new region — fetching on a
        // miss — without any cache clear (#212). `drop(1)` skips the region already loaded on subscribe.
        viewModelScope.launch {
            regionRepository.observeSelectedCountry()
                .map { it.code }
                .distinctUntilChanged()
                .drop(1)
                .collect { retry() }
        }
    }

    fun retry() {
        retryTrigger.update { it + 1 }
    }

    /** Open the game-centric peek sheet for a deal row (it carries the ITAD gameId + title + thumb). */
    fun peekGame(gameId: String, gameName: String, thumb: String?) {
        gamePeekController.load(viewModelScope, gameId, gameName, thumb)
    }

    /** Toggle a game on/off the ignore list from the peek sheet; prompts sign-in when logged out. */
    fun toggleIgnore(gameId: String) {
        viewModelScope.launch {
            if (ignoredRepository.toggleIgnored(gameId) == RepoUpdateResult.NOT_LOGGED_IN) {
                events.tryEmit(StoreUiEvent.SignInRequired)
            }
        }
    }

    /** Toggle a game on/off the waitlist from the peek sheet; prompts sign-in when logged out. */
    fun toggleWaitlist(gameId: String) {
        viewModelScope.launch {
            if (waitlistRepository.toggleWaitlist(gameId) == RepoUpdateResult.NOT_LOGGED_IN) {
                events.tryEmit(StoreUiEvent.SignInRequired)
            }
        }
    }

    /** Toggle a game in/out of the collection from the peek sheet; prompts sign-in when logged out. */
    fun toggleCollection(gameId: String) {
        viewModelScope.launch {
            if (collectionRepository.toggleCollection(gameId) == RepoUpdateResult.NOT_LOGGED_IN) {
                events.tryEmit(StoreUiEvent.SignInRequired)
            }
        }
    }

    fun dismissPeek() {
        gamePeekController.dismiss(viewModelScope)
    }

    fun retryPeek() {
        gamePeek.value?.let { peek -> peekGame(peek.gameId, peek.gameName, peek.thumb) }
    }

    fun onShareClicked(data: GamePeekSheetData.Data) {
        val best = data.bestDeal ?: return
        val text = dealShareTextBuilder.build(
            gameTitle = data.gameName,
            salePriceDenominated = best.deal.priceDenominated,
            storeName = best.store.storeName,
            dealUrl = best.deal.url,
        )
        info(logger, tag = "store_deal_shared") { "gameId=${data.gameId} store=${best.store.storeName}" }
        events.tryEmit(StoreUiEvent.ShareDeal(text))
    }

    internal sealed interface StoreUiEvent {
        data class ShareDeal(val text: String) : StoreUiEvent
        data object SignInRequired : StoreUiEvent
    }

    sealed class StoreScreenData {
        data object Loading : StoreScreenData()
        data object Error : StoreScreenData()
        data class Data(val store: Store) : StoreScreenData()
    }
}
