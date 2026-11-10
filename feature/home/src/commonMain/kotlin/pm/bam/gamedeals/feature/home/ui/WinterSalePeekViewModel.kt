package pm.bam.gamedeals.feature.home.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pm.bam.gamedeals.common.ui.deal.GamePeekController
import pm.bam.gamedeals.common.ui.deal.GamePeekSheetData
import pm.bam.gamedeals.common.ui.share.DealShareTextBuilder
import pm.bam.gamedeals.domain.models.RepoUpdateResult
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.repositories.collection.CollectionRepository
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.ignored.IgnoredRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.info

/**
 * The Winter Sale guide's game peek: the same bottom sheet, library state and actions as Home and Deals, so a guide
 * row behaves like every other game row in the app. Kept apart from [WinterSaleGuideViewModel], which owns the
 * event and price loading.
 */
internal class WinterSalePeekViewModel(
    gamesRepository: GamesRepository,
    storesRepository: StoresRepository,
    private val waitlistRepository: WaitlistRepository,
    private val collectionRepository: CollectionRepository,
    private val ignoredRepository: IgnoredRepository,
    private val dealShareTextBuilder: DealShareTextBuilder,
    private val logger: Logger,
) : ViewModel() {

    private val controller = GamePeekController(gamesRepository, storesRepository, logger)
    val gamePeek: StateFlow<GamePeekSheetData?> = controller.data

    val waitlistIds: StateFlow<ImmutableSet<String>> = waitlistRepository.observeWaitlistIds()
        .onStart { emit(persistentSetOf()) }
        .catch { emit(persistentSetOf()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), persistentSetOf())

    val collectionIds: StateFlow<ImmutableSet<String>> = collectionRepository.observeCollectionIds()
        .onStart { emit(persistentSetOf()) }
        .catch { emit(persistentSetOf()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), persistentSetOf())

    val ignoredIds: StateFlow<ImmutableSet<String>> = ignoredRepository.observeIgnoredIds()
        .onStart { emit(persistentSetOf()) }
        .catch { emit(persistentSetOf()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), persistentSetOf())

    /** Store logos for the rows, keyed by ITAD shop id. */
    val stores: StateFlow<ImmutableMap<Int, Store>> = storesRepository.observeStores()
        .map { list -> list.associateBy { it.storeID }.toImmutableMap() }
        .onStart { emit(persistentMapOf()) }
        .catch { emit(persistentMapOf()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), persistentMapOf())

    val events: SharedFlow<Event>
        field = MutableSharedFlow<Event>(replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    fun peek(gameId: String, gameName: String, thumb: String?) = controller.load(viewModelScope, gameId, gameName, thumb)

    fun retry() {
        gamePeek.value?.let { controller.load(viewModelScope, it.gameId, it.gameName, it.thumb) }
    }

    fun dismiss() = controller.dismiss(viewModelScope)

    fun toggleWaitlist(gameId: String) = launchLibraryWrite { waitlistRepository.toggleWaitlist(gameId) }

    fun toggleCollection(gameId: String) = launchLibraryWrite { collectionRepository.toggleCollection(gameId) }

    fun toggleIgnore(gameId: String) = launchLibraryWrite { ignoredRepository.toggleIgnored(gameId) }

    fun share(data: GamePeekSheetData.Data) {
        val best = data.bestDeal ?: return
        val text = dealShareTextBuilder.build(
            gameTitle = data.gameName,
            salePriceDenominated = best.deal.priceDenominated,
            storeName = best.store.storeName,
            dealUrl = best.deal.url,
        )
        info(logger, tag = "deal_shared") { "gameId=${data.gameId} store=${best.store.storeName} source=winter_sale" }
        events.tryEmit(Event.Share(text))
    }

    private fun launchLibraryWrite(write: suspend () -> RepoUpdateResult) {
        viewModelScope.launch {
            when (write()) {
                RepoUpdateResult.NOT_LOGGED_IN -> events.tryEmit(Event.SignInRequired)
                RepoUpdateResult.FAILED -> events.tryEmit(Event.ActionFailed)
                RepoUpdateResult.UPDATED -> Unit
            }
        }
    }

    sealed interface Event {
        data class Share(val text: String) : Event
        data object SignInRequired : Event
        data object ActionFailed : Event
    }
}
