package pm.bam.gamedeals.feature.game.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pm.bam.gamedeals.common.delayOnStart
import pm.bam.gamedeals.common.logFlow
import pm.bam.gamedeals.common.toFlow
import pm.bam.gamedeals.common.ui.share.DealShareTextBuilder
import pm.bam.gamedeals.domain.models.GameDetails
import pm.bam.gamedeals.domain.models.IgdbGame
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.repositories.favourites.FavouritesRepository
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.igdb.IgdbRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.info

@OptIn(ExperimentalCoroutinesApi::class)
internal class GameViewModel(
    savedStateHandle: SavedStateHandle,
    private val logger: Logger,
    private val gamesRepository: GamesRepository,
    private val storesRepository: StoresRepository,
    private val dealShareTextBuilder: DealShareTextBuilder,
    private val favouritesRepository: FavouritesRepository,
    private val igdbRepository: IgdbRepository,
) : ViewModel() {

    // We store and react to the GameId changes so that only a single 'game deals' flow can exists.
    private val gameIdFlow = MutableStateFlow(savedStateHandle.get<String>("gameId"))

    val isFavourite: StateFlow<Boolean> = gameIdFlow
        .flatMapLatest { id ->
            if (id == null) flowOf(false) else favouritesRepository.observeIsFavourite(id)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val uiState: StateFlow<GameScreenData>
        field = MutableStateFlow<GameScreenData>(GameScreenData.Loading)

    private val reloadTrigger = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    val events: SharedFlow<GameUiEvent>
        field = MutableSharedFlow<GameUiEvent>(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
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
                .collect { uiState.emit(it) }
        }
    }


    fun reloadGameDetails() {
        reloadTrigger.tryEmit(Unit)
    }

    fun toggleFavourite() {
        val current = uiState.value as? GameScreenData.Data ?: return
        val info = current.gameDetails.info
        val id = gameIdFlow.value ?: return
        viewModelScope.launch {
            favouritesRepository.toggleFavourite(
                gameId = id,
                title = info.title,
                thumb = info.thumb,
            )
        }
    }

    fun onShareDealClicked(
        gameInfo: GameDetails.GameInfo,
        store: Store,
        deal: GameDetails.GameDeal,
    ) {
        val text = dealShareTextBuilder.build(
            gameTitle = gameInfo.title,
            salePriceDenominated = deal.priceDenominated,
            storeName = store.storeName,
            dealUrl = deal.url,
        )
        info(logger, tag = "deal_shared") { "dealId=${deal.dealID} store=${store.storeName}" }
        events.tryEmit(GameUiEvent.ShareDeal(text))
    }

    private fun loadGameDetailsFlow(gameId: String) =
        flowOf(gameId)
            .flatMapLatest { gamesRepository.getGameDetails(it).toFlow() }
            .flatMapLatest { details ->
                val dealDetails = details.deals
                    .map { deal -> StoreDealPair(store = storesRepository.getStore(deal.storeID), deal = deal) }
                val igdbGame = details.info.steamAppID?.let { steamId -> fetchIgdbGameSafely(steamId) }
                GameScreenData.Data(details, dealDetails.toImmutableList(), igdbGame).toFlow<GameScreenData>()
            }
            .onStart { uiState.emit(GameScreenData.Loading) }
            .logFlow(logger)
            .catch { emit(GameScreenData.Error) }

    // IGDB enrichment is best-effort. The deal data is the primary content of the screen; an IGDB
    // failure (network, auth, rate limit) must NOT hide it. Cancellation still propagates.
    private suspend fun fetchIgdbGameSafely(steamId: Int): IgdbGame? = try {
        igdbRepository.fetchGameBySteamId(steamId)
    } catch (ce: CancellationException) {
        throw ce
    } catch (_: Throwable) {
        null
    }


    internal sealed interface GameUiEvent {
        data class ShareDeal(val text: String) : GameUiEvent
    }

    sealed class GameScreenData {
        data object Loading : GameScreenData()
        data object Error : GameScreenData()

        @Immutable
        data class Data(
            val gameDetails: GameDetails,
            val dealDetails: ImmutableList<StoreDealPair>,
            val igdbGame: IgdbGame? = null,
        ) : GameScreenData()
    }
}