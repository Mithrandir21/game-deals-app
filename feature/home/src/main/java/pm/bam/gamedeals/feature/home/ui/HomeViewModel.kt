package pm.bam.gamedeals.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
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
import javax.inject.Inject

internal const val LIMIT_DEALS = 10
internal const val LIMIT_GIVEAWAYS = 5
internal val topStores = listOf(1, 11, 3, 23, 15, 27, 7, 21, 2)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class HomeViewModel @Inject constructor(
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

    init {
        viewModelScope.launch {
            loadTopStoreDataFlow()
                .collect { _uiState.emit(it) }
        }
    }

    fun loadTopStoresDeals() =
        viewModelScope.launch {
            loadTopStoreDataFlow()
                .logFlow(logger)
                .onStart { emit(_uiState.value.copy(state = HomeScreenStatus.LOADING)) }
                .collect { _uiState.emit(it) }
        }

    fun onReleaseGame(releaseTitle: String) =
        viewModelScope.launch {
            flow { emit(gamesRepository.getReleaseGameId(releaseTitle)) }
                .onStart { _uiState.emit(_uiState.value.copy(state = HomeScreenStatus.LOADING)) }
                .map { gameId -> gameId ?: throw IllegalStateException("Game not found") }
                .onError { fatal(logger, it) }
                .onCompletion {
                    when (it == null) {
                        true -> _uiState.emit(_uiState.value.copy(state = HomeScreenStatus.SUCCESS))
                        else -> _uiState.emit(_uiState.value.copy(state = HomeScreenStatus.ERROR))
                    }
                }
                .collect { _events.emit(HomeUiEvent.NavigateToGame(it)) }
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
            .map { listOfStores -> listOfStores.map { it to dealsRepository.getStoreDeals(it.storeID, LIMIT_DEALS) } }
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
            .map { HomeScreenData(state = HomeScreenStatus.SUCCESS, releases = it.first, giveaways = it.second, items = it.third) }
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

    internal data class HomeScreenData(
        val state: HomeScreenStatus = HomeScreenStatus.LOADING,
        val releases: List<Release> = emptyList(),
        val giveaways: List<Giveaway> = emptyList(),
        val items: List<HomeScreenListData> = emptyList()
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