package pm.bam.gamedeals.feature.game.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import pm.bam.gamedeals.common.logFlow
import pm.bam.gamedeals.domain.models.IgdbGame
import pm.bam.gamedeals.domain.repositories.igdb.IgdbRepository
import pm.bam.gamedeals.logging.Logger

@OptIn(ExperimentalCoroutinesApi::class)
internal class GameDetailsViewModel(
    savedStateHandle: SavedStateHandle,
    private val logger: Logger,
    private val igdbRepository: IgdbRepository,
) : ViewModel() {

    private val steamAppId: Int? = savedStateHandle.get<Int>("steamAppId")

    val uiState: StateFlow<GameDetailsScreenData>
        field = MutableStateFlow<GameDetailsScreenData>(GameDetailsScreenData.Loading)

    private val reloadTrigger = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

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

    private fun loadFlow(): Flow<GameDetailsScreenData> = flow {
        if (steamAppId == null) {
            emit(GameDetailsScreenData.Error)
            return@flow
        }
        emit(GameDetailsScreenData.Loading)
        val game = igdbRepository.fetchGameDetailsBySteamId(steamAppId)
        emit(if (game != null) GameDetailsScreenData.Data(game) else GameDetailsScreenData.Error)
    }.catch { emit(GameDetailsScreenData.Error) }

    sealed class GameDetailsScreenData {
        data object Loading : GameDetailsScreenData()
        data object Error : GameDetailsScreenData()
        data class Data(val game: IgdbGame) : GameDetailsScreenData()
    }
}
