package pm.bam.gamedeals.feature.game.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import pm.bam.gamedeals.common.favicon.FaviconResolver
import pm.bam.gamedeals.common.logFlow
import pm.bam.gamedeals.domain.models.IgdbGame
import pm.bam.gamedeals.domain.repositories.igdb.IgdbRepository
import pm.bam.gamedeals.logging.Logger

@OptIn(ExperimentalCoroutinesApi::class)
internal class GameDetailsViewModel(
    savedStateHandle: SavedStateHandle,
    private val logger: Logger,
    private val igdbRepository: IgdbRepository,
    private val faviconResolver: FaviconResolver,
) : ViewModel() {

    private val steamAppId: Int? = savedStateHandle.get<Int>("steamAppId")
    private val igdbGameId: Long? = savedStateHandle.get<Long>("igdbGameId")

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
        emit(GameDetailsScreenData.Loading)
        val game = when {
            steamAppId != null -> igdbRepository.fetchGameDetailsBySteamId(steamAppId)
            igdbGameId != null -> igdbRepository.fetchGameDetailsByIgdbId(igdbGameId)
            else -> {
                emit(GameDetailsScreenData.Error)
                return@flow
            }
        }
        emit(
            if (game != null) {
                GameDetailsScreenData.Data(
                    game = game,
                    websites = game.websites.map { it.toUi() }.toImmutableList(),
                )
            } else {
                GameDetailsScreenData.Error
            }
        )
    }.catch { emit(GameDetailsScreenData.Error) }

    // Member extension — has access to `faviconResolver` via the enclosing class.
    private fun IgdbGame.IgdbWebsite.toUi(): WebsiteUiModel {
        val ref = faviconResolver.resolve(url)
        return WebsiteUiModel(
            url = url,
            category = category,
            faviconUrl = ref.url,
            faviconCacheKey = ref.cacheKey,
        )
    }

    sealed class GameDetailsScreenData {
        data object Loading : GameDetailsScreenData()
        data object Error : GameDetailsScreenData()
        data class Data(
            val game: IgdbGame,
            val websites: ImmutableList<WebsiteUiModel> = persistentListOf(),
        ) : GameDetailsScreenData()
    }
}

internal data class WebsiteUiModel(
    val url: String,
    val category: IgdbGame.IgdbWebsite.Category,
    val faviconUrl: String?,
    val faviconCacheKey: String?,
)
