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
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.igdb.IgdbRepository
import pm.bam.gamedeals.logging.Logger

@OptIn(ExperimentalCoroutinesApi::class)
internal class GameDetailsViewModel(
    savedStateHandle: SavedStateHandle,
    private val logger: Logger,
    private val igdbRepository: IgdbRepository,
    private val gamesRepository: GamesRepository,
    private val faviconResolver: FaviconResolver,
) : ViewModel() {

    private val steamAppId: Int? = savedStateHandle.get<Int>("steamAppId")
    private val igdbGameId: Long? = savedStateHandle.get<Long>("igdbGameId")
    // Treat blank/whitespace-only strings as "no title" so the cascade doesn't waste an HTTP call
    // and the NoMatch card doesn't render with an empty title fragment.
    private val title: String? = savedStateHandle.get<String>("title")?.takeIf { it.isNotBlank() }

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
        if (steamAppId == null && igdbGameId == null && title == null) {
            emit(GameDetailsScreenData.Error)
            return@flow
        }
        // Cascade: Steam id → IGDB id → title. If Steam id misses (e.g. CheapShark's
        // "steamAppID" is actually a Steam sub/bundle id that IGDB doesn't track) AND we
        // also have a title, fall back to title lookup. resolvedByTitle is true when the
        // record we ended up with came via the title path — surfaces the fuzzy-match warning.
        var resolvedByTitle = false
        var game = steamAppId?.let { igdbRepository.fetchGameDetailsBySteamId(it) }
        if (game == null && steamAppId != null && title != null) {
            game = igdbRepository.fetchGameDetailsByTitle(title)
            if (game != null) resolvedByTitle = true
        }
        if (game == null && igdbGameId != null) {
            game = igdbRepository.fetchGameDetailsByIgdbId(igdbGameId)
        }
        if (game == null && steamAppId == null && igdbGameId == null && title != null) {
            game = igdbRepository.fetchGameDetailsByTitle(title)
            if (game != null) resolvedByTitle = true
        }
        if (game == null) {
            // Any path that had a title to display lands on the NoMatch explainer.
            emit(if (title != null) GameDetailsScreenData.NoMatch(title) else GameDetailsScreenData.Error)
            return@flow
        }
        emit(
            GameDetailsScreenData.Data(
                game = game,
                websites = game.websites.map { it.toUi() }.toImmutableList(),
                resolvedByTitle = resolvedByTitle,
            )
        )
    }.catch { emit(GameDetailsScreenData.Error) }

    suspend fun resolveDealsAction(): DealsAction? {
        val data = (uiState.value as? GameDetailsScreenData.Data) ?: return null
        val steamAppId = data.game.steamAppId
        if (steamAppId != null) {
            val gameId = gamesRepository.findGameIdBySteamAppId(steamAppId, data.game.name)
            if (gameId != null) return DealsAction.OpenGame(gameId)
        }
        return DealsAction.SearchByTitle(data.game.name)
    }

    fun onWarningTap() {
        val current = uiState.value as? GameDetailsScreenData.Data ?: return
        if (!current.resolvedByTitle) return
        val t = title ?: return

        uiState.value = current.copy(showPicker = true)

        if (current.candidatesState is CandidatesState.Loaded || current.candidatesState is CandidatesState.Loading) return

        viewModelScope.launch {
            val pre = uiState.value as? GameDetailsScreenData.Data ?: return@launch
            uiState.value = pre.copy(candidatesState = CandidatesState.Loading)
            val next: CandidatesState = try {
                CandidatesState.Loaded(igdbRepository.fetchSearchCandidatesByTitle(t))
            } catch (_: Throwable) {
                CandidatesState.Error
            }
            val after = uiState.value as? GameDetailsScreenData.Data ?: return@launch
            uiState.value = after.copy(candidatesState = next)
        }
    }

    fun onPickerDismiss() {
        val current = uiState.value as? GameDetailsScreenData.Data ?: return
        uiState.value = current.copy(showPicker = false)
    }

    fun onCandidatePicked(igdbGameId: Long) {
        val current = uiState.value as? GameDetailsScreenData.Data ?: return
        if (current.game.id == igdbGameId) {
            uiState.value = current.copy(showPicker = false)
            return
        }
        val preservedResolvedByTitle = current.resolvedByTitle
        val preservedCandidates = current.candidatesState
        viewModelScope.launch {
            uiState.value = GameDetailsScreenData.Loading
            val swapped = try {
                igdbRepository.fetchGameDetailsByIgdbId(igdbGameId)
            } catch (_: Throwable) {
                uiState.value = GameDetailsScreenData.Error
                return@launch
            }
            uiState.value = if (swapped != null) {
                GameDetailsScreenData.Data(
                    game = swapped,
                    websites = swapped.websites.map { it.toUi() }.toImmutableList(),
                    resolvedByTitle = preservedResolvedByTitle,
                    candidatesState = preservedCandidates,
                )
            } else {
                GameDetailsScreenData.Error
            }
        }
    }

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
        data class NoMatch(val title: String) : GameDetailsScreenData()
        data class Data(
            val game: IgdbGame,
            val websites: ImmutableList<WebsiteUiModel> = persistentListOf(),
            val resolvedByTitle: Boolean = false,
            val candidatesState: CandidatesState = CandidatesState.Idle,
            val showPicker: Boolean = false,
        ) : GameDetailsScreenData()
    }

    sealed class DealsAction {
        data class OpenGame(val gameId: String) : DealsAction()
        data class SearchByTitle(val title: String) : DealsAction()
    }

    sealed class CandidatesState {
        data object Idle : CandidatesState()
        data object Loading : CandidatesState()
        data class Loaded(val items: ImmutableList<IgdbGame.IgdbSimilarGame>) : CandidatesState()
        data object Error : CandidatesState()
    }
}

internal data class WebsiteUiModel(
    val url: String,
    val category: IgdbGame.IgdbWebsite.Category,
    val faviconUrl: String?,
    val faviconCacheKey: String?,
)
