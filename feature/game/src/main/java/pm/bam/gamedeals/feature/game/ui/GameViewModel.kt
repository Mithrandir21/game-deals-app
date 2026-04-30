package pm.bam.gamedeals.feature.game.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pm.bam.gamedeals.common.delayOnStart
import pm.bam.gamedeals.common.logFlow
import pm.bam.gamedeals.common.toFlow
import pm.bam.gamedeals.domain.models.GameDetails
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.logging.Logger
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class GameViewModel @Inject constructor(
    private val logger: Logger,
    private val gamesRepository: GamesRepository,
    private val storesRepository: StoresRepository
) : ViewModel() {

    // We store and react to the GameId changes so that only a single 'game deals' flow can exists
    private val gameIdFlow = MutableStateFlow<Int?>(null)

    private val _uiState = MutableStateFlow<GameScreenData>(GameScreenData.Loading)
    val uiState: StateFlow<GameScreenData> = _uiState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = GameScreenData.Loading
    )

    init {
        viewModelScope.launch {
            gameIdFlow
                .filterNotNull() // Skip our initial null value
                .distinctUntilChanged() // Skip fetching if storeId is the same, like on orientation change
                .delayOnStart(1000)
                .flatMapLatest { loadGameDetailsFlow(it) }
                .logFlow(logger)
                .collect { _uiState.emit(it) }
        }
    }


    fun reloadGameDetails(gameId: Int) {
        viewModelScope.launch {
            loadGameDetailsFlow(gameId)
                .collect { _uiState.emit(it) }
        }
    }

    private fun loadGameDetailsFlow(gameId: Int) =
        flowOf(gameId)
            .flatMapLatest { gamesRepository.getGameDetails(it).toFlow() }
            .flatMapLatest { details ->
                details.deals
                    .map { deal -> storesRepository.getStore(deal.storeID) to deal }
                    .let { dealDetails -> GameScreenData.Data(details, dealDetails) }
                    .toFlow<GameScreenData>()
            }
            .onStart { _uiState.emit(GameScreenData.Loading) }
            .logFlow(logger)
            .catch { emit(GameScreenData.Error) }


    fun loadGameDetails(gameId: Int) = gameIdFlow.update { gameId }

    sealed class GameScreenData {
        data object Loading : GameScreenData()
        data object Error : GameScreenData()
        data class Data(
            val gameDetails: GameDetails,
            val dealDetails: List<Pair<Store, GameDetails.GameDeal>>
        ) : GameScreenData()
    }
}