package pm.bam.gamedeals.feature.home.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pm.bam.gamedeals.common.logFlow
import pm.bam.gamedeals.common.onError
import pm.bam.gamedeals.common.ui.deal.DealBottomSheetData
import pm.bam.gamedeals.common.ui.deal.DealDetailsController
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.Giveaway
import pm.bam.gamedeals.domain.models.Release
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.repositories.deals.DealsRepository
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.giveaway.GiveawaysRepository
import pm.bam.gamedeals.domain.repositories.releases.ReleasesRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.fatal

internal const val LIMIT_DEALS = 10
internal const val LIMIT_GIVEAWAYS = 5
internal val topStores = listOf(1, 11, 3, 23, 15, 27, 7, 21, 2)

@OptIn(ExperimentalCoroutinesApi::class)
internal class HomeViewModel(
    private val storesRepository: StoresRepository,
    private val dealsRepository: DealsRepository,
    private val gamesRepository: GamesRepository,
    private val releasesRepository: ReleasesRepository,
    private val giveawaysRepository: GiveawaysRepository,
    private val logger: Logger
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeScreenData())
    val uiState: StateFlow<HomeScreenData> = _uiState.asStateFlow()

    private val dealDetailsController = DealDetailsController(dealsRepository, storesRepository, logger)
    val dealDetails: StateFlow<DealBottomSheetData?> = dealDetailsController.dealDetails

    private val _events = MutableSharedFlow<HomeUiEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<HomeUiEvent> = _events.asSharedFlow()

    private var loadJob: Job? = null

    init {
        loadTopStoresDeals()
    }

    fun loadTopStoresDeals() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            loadTopStoreDataFlow()
                .onStart { _uiState.update { it.copy(state = HomeScreenStatus.LOADING) } }
                .collect { newState -> _uiState.emit(newState) }
        }
    }

    fun onReleaseGame(releaseTitle: String) =
        viewModelScope.launch {
            flow { emit(gamesRepository.getReleaseGameId(releaseTitle)) }
                .onStart { _uiState.update { it.copy(state = HomeScreenStatus.LOADING) } }
                .onError { fatal(logger, it) }
                .catch { _uiState.update { current -> current.copy(state = HomeScreenStatus.ERROR) } }
                .collect { gameId ->
                    if (gameId == null) {
                        _uiState.update { current -> current.copy(state = HomeScreenStatus.ERROR) }
                    } else {
                        _uiState.update { current -> current.copy(state = HomeScreenStatus.SUCCESS) }
                        _events.emit(HomeUiEvent.NavigateToGame(gameId))
                    }
                }
        }

    fun loadDealDetails(dealId: String, dealStoreId: Int, dealTitle: String, dealPriceDenominated: String) {
        dealDetailsController.load(viewModelScope, dealId, dealStoreId, dealTitle, dealPriceDenominated)
    }

    fun dismissDealDetails() {
        dealDetailsController.dismiss(viewModelScope)
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
            .onStart { giveawaysRepository.refreshGiveaways() }
            .logFlow(logger)
            .catch { emit(emptyList()) }

    internal sealed interface HomeUiEvent {
        data class NavigateToGame(val gameId: Int) : HomeUiEvent
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