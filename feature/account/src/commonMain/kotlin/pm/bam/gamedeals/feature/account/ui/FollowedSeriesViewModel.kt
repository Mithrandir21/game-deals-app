package pm.bam.gamedeals.feature.account.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pm.bam.gamedeals.domain.models.IgdbImageSize
import pm.bam.gamedeals.domain.models.igdbImageUrl
import pm.bam.gamedeals.domain.repositories.franchise.FollowedFranchiseChecker
import pm.bam.gamedeals.domain.repositories.franchise.FollowedFranchiseRepository
import pm.bam.gamedeals.domain.repositories.franchise.FranchiseSaleSnapshotStore
import pm.bam.gamedeals.domain.repositories.igdb.IgdbRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.error

/**
 * One game inside a followed series — its IGDB id (for navigation) + cover (for the tile). When the game is
 * currently on sale (joined from the followed-franchise sale snapshot), [cutPercent] + [priceDenominated]
 * are set so the tile shows a deal badge; null otherwise.
 */
@Immutable
internal data class FollowedSeriesGame(
    val igdbGameId: Long,
    val title: String,
    val coverUrl: String?,
    val cutPercent: Int? = null,
    val priceDenominated: String? = null,
) {
    val onSale: Boolean get() = cutPercent != null
}

/** One followed franchise/series and (lazily) the games IGDB lists under it. */
@Immutable
internal data class FollowedSeriesItem(
    val franchiseId: Long,
    val name: String,
    val games: ImmutableList<FollowedSeriesGame> = persistentListOf(),
)

@Immutable
internal data class FollowedSeriesState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val items: ImmutableList<FollowedSeriesItem> = persistentListOf(),
)

/**
 * Backs the Followed-series sub-screen (#7): observes the local follows and resolves each franchise's games
 * from IGDB so the user can browse and unfollow them. Also surfaces **current franchise sales** (#7
 * notification revamp) by joining the cached [FranchiseSaleSnapshotStore] onto those games — on-sale games
 * get a price/cut badge and float to the front. A pull-to-refresh recomputes the snapshot via
 * [FollowedFranchiseChecker.currentOnSale] (the expensive IGDB→ITAD→price pipeline) and rewrites the cache.
 *
 * Per-franchise IGDB game lists are cached in memory so re-emits (an unfollow, or a snapshot refresh) don't
 * re-fetch them.
 */
internal class FollowedSeriesViewModel(
    private val followedFranchiseRepository: FollowedFranchiseRepository,
    private val igdbRepository: IgdbRepository,
    private val snapshotStore: FranchiseSaleSnapshotStore,
    private val franchiseChecker: FollowedFranchiseChecker,
    private val logger: Logger,
) : ViewModel() {

    val uiState: StateFlow<FollowedSeriesState>
        field = MutableStateFlow(FollowedSeriesState(loading = true))

    private val gamesCache = mutableMapOf<Long, List<FollowedSeriesGame>>()

    /** The latest on-sale snapshot (cached on open, recomputed on pull-to-refresh). */
    private val snapshot = MutableStateFlow<Map<Long, Pair<Int, String>>>(emptyMap())

    init {
        viewModelScope.launch {
            snapshot.value = loadSnapshot()
        }
        viewModelScope.launch {
            combine(followedFranchiseRepository.observeFollowed(), snapshot) { followed, sales -> followed to sales }
                .collect { (followed, sales) ->
                    val items = followed
                        .sortedByDescending { it.addedAtMs }
                        .map { franchise ->
                            val games = gamesFor(franchise.franchiseId)
                                .map { game ->
                                    val sale = sales[game.igdbGameId]
                                    game.copy(cutPercent = sale?.first, priceDenominated = sale?.second)
                                }
                                // On-sale (highest cut) first, then the rest in IGDB order.
                                .sortedByDescending { it.cutPercent ?: -1 }
                            FollowedSeriesItem(
                                franchiseId = franchise.franchiseId,
                                name = franchise.name,
                                games = games.toImmutableList(),
                            )
                        }
                    uiState.update { it.copy(loading = false, items = items.toImmutableList()) }
                }
        }
    }

    /** Pull-to-refresh: recompute the on-sale snapshot (expensive) and rewrite the cache. */
    fun refresh() {
        viewModelScope.launch {
            uiState.update { it.copy(refreshing = true) }
            val onSale = runCatching { franchiseChecker.currentOnSale() }.getOrElse { error(logger, it); null }
            if (onSale != null) {
                runCatching { snapshotStore.replace(onSale) }
                snapshot.value = onSale.associate { it.igdbGameId to (it.cutPercent to it.priceDenominated) }
            }
            uiState.update { it.copy(refreshing = false) }
        }
    }

    private suspend fun loadSnapshot(): Map<Long, Pair<Int, String>> =
        runCatching { snapshotStore.get() }.getOrDefault(emptyList())
            .associate { it.igdbGameId to (it.cutPercent to it.priceDenominated) }

    private suspend fun gamesFor(franchiseId: Long): List<FollowedSeriesGame> =
        gamesCache.getOrElse(franchiseId) {
            runCatching { igdbRepository.fetchFranchiseGames(franchiseId, GAMES_PER_FRANCHISE) }
                .getOrElse { error(logger, it); emptyList() }
                .map { game ->
                    FollowedSeriesGame(
                        igdbGameId = game.id,
                        title = game.name,
                        coverUrl = game.coverImageId?.let { igdbImageUrl(it, IgdbImageSize.CoverBig) },
                    )
                }
                .also { gamesCache[franchiseId] = it }
        }

    fun unfollow(franchiseId: Long) {
        viewModelScope.launch { followedFranchiseRepository.remove(franchiseId) }
    }

    private companion object {
        const val GAMES_PER_FRANCHISE = 20
    }
}
