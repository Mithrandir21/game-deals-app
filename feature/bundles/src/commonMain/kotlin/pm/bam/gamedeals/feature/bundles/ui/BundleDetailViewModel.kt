package pm.bam.gamedeals.feature.bundles.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import pm.bam.gamedeals.common.ui.deal.GamePeekController
import pm.bam.gamedeals.common.ui.deal.GamePeekSheetData
import pm.bam.gamedeals.common.ui.share.DealShareTextBuilder
import pm.bam.gamedeals.domain.models.Bundle
import pm.bam.gamedeals.domain.models.BundleGamePrice
import pm.bam.gamedeals.domain.models.RepoUpdateResult
import pm.bam.gamedeals.domain.repositories.bundles.BundlesRepository
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.ignored.IgnoredRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.domain.repositories.collection.CollectionRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.domain.utils.formatMoney
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.fatal
import pm.bam.gamedeals.logging.info

/**
 * Resolves a single [Bundle] by id for the detail screen (epic #205, Phase 3c; enriched in the Bundles
 * redesign). The bundle renders immediately ([BundleDetailScreenData.Data]); a best-effort batched
 * `/games/prices/v3` call then fills each game's current best price + all-time low and the "overall value"
 * summary. A price-fetch failure leaves the bundle visible with an empty price map (the rows show "No
 * current deal"). A missing id surfaces as [BundleDetailScreenData.Error].
 */
internal class BundleDetailViewModel(
    savedStateHandle: SavedStateHandle,
    private val logger: Logger,
    private val bundlesRepository: BundlesRepository,
    private val gamesRepository: GamesRepository,
    private val storesRepository: StoresRepository,
    private val waitlistRepository: WaitlistRepository,
    private val collectionRepository: CollectionRepository,
    private val ignoredRepository: IgnoredRepository,
    private val dealShareTextBuilder: DealShareTextBuilder,
) : ViewModel() {

    private val bundleId: Int? = savedStateHandle.get<Int>("bundleId")

    val uiState: StateFlow<BundleDetailScreenData>
        field = MutableStateFlow<BundleDetailScreenData>(BundleDetailScreenData.Loading)

    /** Games on the user's waitlist / collection / ignore list — drive the peek sheet's toggles. */
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

    // The game-centric quick-peek sheet, shared with Home/Deals. Bundle games carry an ITAD id, so no
    // IgdbRepository (title resolution) is needed here.
    private val gamePeekController = GamePeekController(gamesRepository, storesRepository, logger)
    val gamePeek: StateFlow<GamePeekSheetData?> = gamePeekController.data

    val events: SharedFlow<BundleDetailUiEvent>
        field = MutableSharedFlow(
            replay = 0,
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    init {
        load()
    }

    /** Open the quick-peek sheet for a bundle game (it always carries an ITAD id). */
    fun peekGame(gameId: String, gameName: String, thumb: String?) =
        gamePeekController.load(viewModelScope, gameId, gameName, thumb)

    fun dismissPeek() = gamePeekController.dismiss(viewModelScope)

    /** Toggle a game on/off the waitlist from the peek sheet; prompts sign-in when logged out. */
    fun toggleWaitlist(gameId: String) {
        viewModelScope.launch {
            if (waitlistRepository.toggleWaitlist(gameId) == RepoUpdateResult.NOT_LOGGED_IN) {
                events.tryEmit(BundleDetailUiEvent.SignInRequired)
            }
        }
    }

    /** Toggle a game in/out of the collection from the peek sheet; prompts sign-in when logged out. */
    fun toggleCollection(gameId: String) {
        viewModelScope.launch {
            if (collectionRepository.toggleCollection(gameId) == RepoUpdateResult.NOT_LOGGED_IN) {
                events.tryEmit(BundleDetailUiEvent.SignInRequired)
            }
        }
    }

    /** Toggle a game on/off the ignore list from the peek sheet; prompts sign-in when logged out. */
    fun toggleIgnore(gameId: String) {
        viewModelScope.launch {
            if (ignoredRepository.toggleIgnored(gameId) == RepoUpdateResult.NOT_LOGGED_IN) {
                events.tryEmit(BundleDetailUiEvent.SignInRequired)
            }
        }
    }

    fun onShareClicked(data: GamePeekSheetData.Data) {
        val best = data.bestDeal ?: return
        val text = dealShareTextBuilder.build(
            gameTitle = data.gameName,
            salePriceDenominated = best.deal.priceDenominated,
            storeName = best.store.storeName,
            dealUrl = best.deal.url,
        )
        info(logger, tag = "deal_shared") { "gameId=${data.gameId} store=${best.store.storeName}" }
        events.tryEmit(BundleDetailUiEvent.ShareDeal(text))
    }

    fun load() {
        viewModelScope.launch {
            uiState.value = BundleDetailScreenData.Loading
            val id = bundleId
            if (id == null) {
                uiState.value = BundleDetailScreenData.Error
                return@launch
            }
            try {
                val bundle = bundlesRepository.getBundle(id)
                if (bundle == null) {
                    uiState.value = BundleDetailScreenData.Error
                    return@launch
                }
                // Render the bundle first; enrich with prices in a second pass.
                uiState.value = BundleDetailScreenData.Data(bundle = bundle, pricesLoading = bundle.games.isNotEmpty())
                enrichPrices(bundle)
            } catch (e: CancellationException) {
                throw e
            } catch (t: Throwable) {
                fatal(logger, t)
                uiState.value = BundleDetailScreenData.Error
            }
        }
    }

    /** Best-effort price enrichment — a failure keeps the bundle visible with an empty map. */
    private suspend fun enrichPrices(bundle: Bundle) {
        val gameIds = bundle.games.map { it.id }
        if (gameIds.isEmpty()) return
        val prices: List<BundleGamePrice> = try {
            bundlesRepository.getBundleGamePrices(gameIds)
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            fatal(logger, t)
            emptyList()
        }
        val priceMap = prices.associateBy { it.gameId }.toImmutableMap()
        // Only patch the state if we're still showing this bundle (guard against a concurrent reload).
        val current = uiState.value
        if (current is BundleDetailScreenData.Data && current.bundle.id == bundle.id) {
            uiState.value = current.copy(
                prices = priceMap,
                pricesLoading = false,
                valueSummary = buildValueSummary(bundle, priceMap),
            )
        }
    }

    /**
     * The "overall value" totals: sum of current best prices and sum of all-time lows across the bundle's
     * games, vs. the bundle price. Games with no current deal are skipped, so the totals (and therefore the
     * savings %) are a lower bound — [BundleValueSummary.pricedGames] vs [BundleValueSummary.totalGames]
     * lets the UI flag that. Returns null when nothing could be priced.
     */
    private fun buildValueSummary(bundle: Bundle, prices: Map<String, BundleGamePrice>): BundleValueSummary? {
        val priced = prices.values
        val currency = priced.firstNotNullOfOrNull { it.currency } ?: return null
        val currentSum = priced.mapNotNull { it.bestPriceValue }.takeIf { it.isNotEmpty() }?.sum()
        val lowSum = priced.mapNotNull { it.historicalLowValue }.takeIf { it.isNotEmpty() }?.sum()
        if (currentSum == null && lowSum == null) return null
        val savings = bundle.priceValue?.let { bundlePrice ->
            if (currentSum != null && currentSum > 0.0) (((currentSum - bundlePrice) / currentSum) * 100).roundToInt() else null
        }
        return BundleValueSummary(
            currentValueDenominated = currentSum?.let { formatMoney(it, currency) },
            historicalLowDenominated = lowSum?.let { formatMoney(it, currency) },
            bundlePriceDenominated = bundle.priceDenominated,
            savingsPercent = savings,
            pricedGames = priced.count { it.bestPriceValue != null },
            totalGames = bundle.games.size,
        )
    }

    internal sealed interface BundleDetailUiEvent {
        data class ShareDeal(val text: String) : BundleDetailUiEvent
        data object SignInRequired : BundleDetailUiEvent
    }

    sealed class BundleDetailScreenData {
        data object Loading : BundleDetailScreenData()
        data object Error : BundleDetailScreenData()

        @Immutable
        data class Data(
            val bundle: Bundle,
            val prices: ImmutableMap<String, BundleGamePrice> = persistentMapOf(),
            val pricesLoading: Boolean = false,
            val valueSummary: BundleValueSummary? = null,
        ) : BundleDetailScreenData()
    }

    @Immutable
    data class BundleValueSummary(
        val currentValueDenominated: String?,
        val historicalLowDenominated: String?,
        val bundlePriceDenominated: String?,
        val savingsPercent: Int?,
        val pricedGames: Int,
        val totalGames: Int,
    )
}
