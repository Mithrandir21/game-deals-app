package pm.bam.gamedeals.feature.account.ui

import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pm.bam.gamedeals.common.ui.deal.GamePeekController
import pm.bam.gamedeals.common.ui.deal.GamePeekSheetData
import pm.bam.gamedeals.common.ui.share.DealShareTextBuilder
import pm.bam.gamedeals.domain.models.RepoUpdateResult
import pm.bam.gamedeals.domain.repositories.collection.CollectionRepository
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.ignored.IgnoredRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.info

/**
 * Shared game-centric peek-sheet plumbing for the Waitlist and Collection lists, mirroring the per-screen
 * wiring used by Home/Deals/Discover. Tapping a row opens the sheet ([data]); its waitlist / collection /
 * ignore toggles and share run through here, and [events] carry the sign-in / share side effects up to the
 * screen. The id flows drive the sheet's icon states.
 */
internal class GamePeekDelegate(
    private val scope: CoroutineScope,
    gamesRepository: GamesRepository,
    storesRepository: StoresRepository,
    private val waitlistRepository: WaitlistRepository,
    private val collectionRepository: CollectionRepository,
    private val ignoredRepository: IgnoredRepository,
    private val dealShareTextBuilder: DealShareTextBuilder,
    private val logger: Logger,
) {
    private val controller = GamePeekController(gamesRepository, storesRepository, logger)
    val data: StateFlow<GamePeekSheetData?> = controller.data

    val waitlistIds: StateFlow<ImmutableSet<String>> = waitlistRepository.observeWaitlistIds()
        .onStart { emit(persistentSetOf()) }.catch { emit(persistentSetOf()) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), persistentSetOf())

    val collectionIds: StateFlow<ImmutableSet<String>> = collectionRepository.observeCollectionIds()
        .onStart { emit(persistentSetOf()) }.catch { emit(persistentSetOf()) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), persistentSetOf())

    val ignoredIds: StateFlow<ImmutableSet<String>> = ignoredRepository.observeIgnoredIds()
        .onStart { emit(persistentSetOf()) }.catch { emit(persistentSetOf()) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), persistentSetOf())

    val events: SharedFlow<GamePeekEvent>
        field = MutableSharedFlow(replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    fun peek(gameId: String, gameName: String, thumb: String?) = controller.load(scope, gameId, gameName, thumb)

    fun dismiss() = controller.dismiss(scope)

    fun retry() {
        data.value?.let { peek(it.gameId, it.gameName, it.thumb) }
    }

    fun toggleWaitlist(gameId: String) = toggle { waitlistRepository.toggleWaitlist(gameId) }
    fun toggleCollection(gameId: String) = toggle { collectionRepository.toggleCollection(gameId) }
    fun toggleIgnore(gameId: String) = toggle { ignoredRepository.toggleIgnored(gameId) }

    private fun toggle(action: suspend () -> RepoUpdateResult) {
        scope.launch {
            if (action() == RepoUpdateResult.NOT_LOGGED_IN) events.tryEmit(GamePeekEvent.SignInRequired)
        }
    }

    fun share(data: GamePeekSheetData.Data) {
        val best = data.bestDeal ?: return
        val text = dealShareTextBuilder.build(
            gameTitle = data.gameName,
            salePriceDenominated = best.deal.priceDenominated,
            storeName = best.store.storeName,
            dealUrl = best.deal.url,
        )
        info(logger, tag = "library_deal_shared") { "gameId=${data.gameId} store=${best.store.storeName}" }
        events.tryEmit(GamePeekEvent.ShareDeal(text))
    }
}

internal sealed interface GamePeekEvent {
    data object SignInRequired : GamePeekEvent
    data class ShareDeal(val text: String) : GamePeekEvent
}
