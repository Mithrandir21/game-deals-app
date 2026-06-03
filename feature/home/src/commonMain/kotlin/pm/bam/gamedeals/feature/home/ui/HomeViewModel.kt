package pm.bam.gamedeals.feature.home.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pm.bam.gamedeals.common.logFlow
import pm.bam.gamedeals.common.onError
import pm.bam.gamedeals.common.ui.deal.DealBottomSheetData
import pm.bam.gamedeals.common.ui.deal.DealDetailsController
import pm.bam.gamedeals.common.ui.share.DealShareTextBuilder
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.FavouriteGame
import pm.bam.gamedeals.domain.models.Giveaway
import pm.bam.gamedeals.domain.models.Release
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.repositories.deals.DealsRepository
import pm.bam.gamedeals.domain.repositories.favourites.FavouritesRepository
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.giveaway.GiveawaysRepository
import pm.bam.gamedeals.domain.repositories.releases.ReleasesRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.fatal
import pm.bam.gamedeals.logging.info

internal const val LIMIT_DEALS = 10
internal const val LIMIT_GIVEAWAYS = 5
internal const val EXPIRED_STATUS = "Expired"
// ITAD shop ids of major PC stores, shown as the Home "top stores" strips (epic #205, Phase 2b —
// the old values were CheapShark store ids, which don't map to ITAD's). See `/service/shops/v1`:
// 61 Steam · 16 Epic · 35 GOG · 37 Humble · 6 Fanatical · 36 GreenManGaming · 24 GamersGate ·
// 20 GameBillet · 42 IndieGala.
internal val topStores = listOf(61, 16, 35, 37, 6, 36, 24, 20, 42)

@OptIn(ExperimentalCoroutinesApi::class)
internal class HomeViewModel(
    private val storesRepository: StoresRepository,
    private val dealsRepository: DealsRepository,
    private val gamesRepository: GamesRepository,
    private val releasesRepository: ReleasesRepository,
    private val giveawaysRepository: GiveawaysRepository,
    private val favouritesRepository: FavouritesRepository,
    private val dealShareTextBuilder: DealShareTextBuilder,
    private val logger: Logger
) : ViewModel() {

    val uiState: StateFlow<HomeScreenData>
        field = MutableStateFlow(HomeScreenData())

    val favouriteIds: StateFlow<ImmutableSet<String>> = favouritesRepository.observeFavouriteIds()
        .onStart { emit(persistentSetOf()) }
        .catch { emit(persistentSetOf()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), persistentSetOf())

    val favourites: StateFlow<ImmutableList<FavouriteGame>> = favouritesRepository.observeFavourites()
        .map { it.toImmutableList() }
        .onStart { emit(persistentListOf()) }
        .catch { emit(persistentListOf()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), persistentListOf())

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
        loadTopStoresDeals()
    }

    fun loadTopStoresDeals() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            loadTopStoreDataFlow()
                .onStart { uiState.update { it.copy(state = HomeScreenStatus.LOADING) } }
                .collect { newState -> uiState.emit(newState) }
        }
    }

    fun onReleaseGame(releaseTitle: String) {
        viewModelScope.launch {
            flow { emit(gamesRepository.getReleaseDeal(releaseTitle)) }
                .onStart { uiState.update { it.copy(state = HomeScreenStatus.LOADING) } }
                .onError { fatal(logger, it) }
                .catch { uiState.update { current -> current.copy(state = HomeScreenStatus.ERROR) } }
                .collect { deal ->
                    if (deal == null) {
                        uiState.update { current -> current.copy(state = HomeScreenStatus.ERROR) }
                    } else {
                        uiState.update { current -> current.copy(state = HomeScreenStatus.SUCCESS) }
                        loadDealDetails(
                            dealId = deal.dealID,
                            dealStoreId = deal.storeID,
                            dealGameId = deal.gameID,
                            dealTitle = deal.title,
                            dealPriceDenominated = deal.salePriceDenominated,
                            dealUrl = deal.url,
                        )
                    }
                }
        }
    }

    fun loadDealDetails(dealId: String, dealStoreId: Int, dealGameId: String, dealTitle: String, dealPriceDenominated: String, dealUrl: String) {
        dealDetailsController.load(viewModelScope, dealId, dealStoreId, dealGameId, dealTitle, dealPriceDenominated, dealUrl)
    }

    fun toggleFavouriteFromDeal(data: DealBottomSheetData.DealDetailsData) {
        viewModelScope.launch {
            favouritesRepository.toggleFavourite(
                gameId = data.gameId,
                title = data.gameName,
                thumb = data.gameInfo.thumb,
            )
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

    private fun loadTopStoreDataFlow() =
        flow { emitAll(storesRepository.observeStores()) }
            .map { listOfStores -> listOfStores.filter { topStores.contains(it.storeID) } }
            .map { listOfStores ->
                coroutineScope {
                    listOfStores.map { store ->
                        async {
                            store to dealsRepository.getStoreDeals(store.storeID, LIMIT_DEALS)
                        }
                    }.awaitAll()
                }
            }
            .map {
                val data = mutableListOf<HomeScreenListData>()

                it.forEach { (store, deals) ->
                    data.add(HomeScreenListData.StoreData(store))
                    data.addAll(deals.map { deal -> HomeScreenListData.DealData(deal) })
                    data.add(HomeScreenListData.ViewAllData(store))
                }

                return@map data
            }
            .flatMapLatest { loadNewReleases().map { releases -> releases to it } }
            .flatMapLatest { loadGiveaways().map { giveaways -> Triple(it.first, giveaways.take(LIMIT_GIVEAWAYS), it.second) } }
            .map {
                HomeScreenData(
                    state = HomeScreenStatus.SUCCESS,
                    releases = it.first.toImmutableList(),
                    giveaways = it.second.toImmutableList(),
                    items = it.third.toImmutableList()
                )
            }
            .logFlow(logger)
            .catch { emit(HomeScreenData(state = HomeScreenStatus.ERROR)) }

    private fun loadNewReleases(): Flow<List<Release>> =
        flow { emitAll(releasesRepository.observeReleases()) }
            .logFlow(logger)
            .catch { emit(emptyList()) }

    private fun loadGiveaways(): Flow<List<Giveaway>> =
        flow { emitAll(giveawaysRepository.observeGiveaways()) }
            .map { list -> list.filter { !it.status.equals(EXPIRED_STATUS, ignoreCase = true) } }
            .onStart { giveawaysRepository.refreshGiveaways() }
            .logFlow(logger)
            .catch { emit(emptyList()) }

    internal sealed interface HomeUiEvent {
        data class ShareDeal(val text: String) : HomeUiEvent
    }

    @Immutable
    internal data class HomeScreenData(
        val state: HomeScreenStatus = HomeScreenStatus.LOADING,
        val releases: ImmutableList<Release> = persistentListOf(),
        val giveaways: ImmutableList<Giveaway> = persistentListOf(),
        val items: ImmutableList<HomeScreenListData> = persistentListOf()
    )

    internal enum class HomeScreenStatus {
        LOADING, ERROR, SUCCESS
    }

    internal sealed class HomeScreenListData {
        data class StoreData(val store: Store) : HomeScreenListData()
        data class DealData(val deal: Deal) : HomeScreenListData()
        data class ViewAllData(val store: Store) : HomeScreenListData()
    }
}