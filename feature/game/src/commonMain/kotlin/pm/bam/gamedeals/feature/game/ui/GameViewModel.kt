package pm.bam.gamedeals.feature.game.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import pm.bam.gamedeals.common.delayOnStart
import pm.bam.gamedeals.common.logFlow
import pm.bam.gamedeals.common.toFlow
import pm.bam.gamedeals.domain.models.GameDetails
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.logging.Logger

@OptIn(ExperimentalCoroutinesApi::class)
internal class GameViewModel(
    savedStateHandle: SavedStateHandle,
    private val logger: Logger,
    private val gamesRepository: GamesRepository,
    private val storesRepository: StoresRepository
) : ViewModel() {

    private val gameIdFlow = MutableStateFlow(savedStateHandle.get<Int>("gameId"))

    private val _uiState = MutableStateFlow<GameScreenData>(GameScreenData.Loading)
    val uiState: StateFlow<GameScreenData> = _uiState.asStateFlow()

    private val reloadTrigger = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    init {
        viewModelScope.launch {
            combine(
                gameIdFlow,
                reloadTrigger.onStart { emit(Unit) },
            ) { id, _ -> id }
                .delayOnStart(1000)
                .flatMapLatest { id ->
                    when (id) {
                        null -> flowOf(GameScreenData.Error)
                        else -> loadGameDetailsFlow(id)
                    }
                }
                .logFlow(logger)
                .collect { _uiState.emit(it) }
        }
    }


    fun reloadGameDetails() {
        reloadTrigger.tryEmit(Unit)
    }

    private fun loadGameDetailsFlow(gameId: Int) =
        flowOf(gameId)
            .flatMapLatest { gamesRepository.getGameDetails(it).toFlow() }
            .flatMapLatest { details ->
                details.deals
                    .map { deal -> storesRepository.getStore(deal.storeID) to deal }
                    .let { dealDetails -> GameScreenData.Data(details, dealDetails.toImmutableList()) }
                    .toFlow<GameScreenData>()
            }
            .onStart { _uiState.emit(GameScreenData.Loading) }
            .logFlow(logger)
            .catch { emit(GameScreenData.Error) }


    sealed class GameScreenData {
        data object Loading : GameScreenData()
        data object Error : GameScreenData()

        @Immutable
        data class Data(
            val gameDetails: GameDetails,
            val dealDetails: ImmutableList<Pair<Store, GameDetails.GameDeal>>
        ) : GameScreenData()
    }
}