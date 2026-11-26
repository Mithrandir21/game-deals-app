package pm.bam.gamedeals.feature.account.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pm.bam.gamedeals.domain.models.AuthState
import pm.bam.gamedeals.domain.models.FollowedFranchise
import pm.bam.gamedeals.domain.models.IgdbImageSize
import pm.bam.gamedeals.domain.models.igdbImageUrl
import pm.bam.gamedeals.domain.repositories.account.AccountRepository
import pm.bam.gamedeals.domain.repositories.collection.CollectionRepository
import pm.bam.gamedeals.domain.repositories.franchise.FollowedFranchiseChecker
import pm.bam.gamedeals.domain.repositories.franchise.FollowedFranchiseRepository
import pm.bam.gamedeals.domain.repositories.franchise.FranchiseSaleSnapshotStore
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.igdb.IgdbRepository
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
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
    /** Resolved ITAD id (Steam-appid bridge), or null when the entry can't be priced/owned-checked. */
    val itadGameId: String? = null,
    /** True when this entry is in the user's ITAD collection (only meaningful while logged in). */
    val owned: Boolean = false,
    val cutPercent: Int? = null,
    val priceDenominated: String? = null,
) {
    val onSale: Boolean get() = cutPercent != null
}

/**
 * One followed franchise/series and (lazily) the games IGDB lists under it. [ownedCount] / [resolvableCount]
 * back the "you own X of Y" backlog line — [resolvableCount] excludes entries with no ITAD match (non-Steam
 * members), which can't be owned-checked and would otherwise read as falsely unowned.
 */
@Immutable
internal data class FollowedSeriesItem(
    val franchiseId: Long,
    val name: String,
    val games: ImmutableList<FollowedSeriesGame> = persistentListOf(),
    val ownedCount: Int = 0,
    val resolvableCount: Int = 0,
) {
    /** Priceable entries the user doesn't own yet — the actionable backlog. */
    val missingCount: Int get() = (resolvableCount - ownedCount).coerceAtLeast(0)
}

@Immutable
internal data class FollowedSeriesState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    /** Ownership backlog is only meaningful (and only shown) when signed in to ITAD. */
    val loggedIn: Boolean = false,
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
    private val regionRepository: RegionRepository,
    private val collectionRepository: CollectionRepository,
    private val accountRepository: AccountRepository,
    private val gamesRepository: GamesRepository,
    private val logger: Logger,
) : ViewModel() {

    val uiState: StateFlow<FollowedSeriesState>
        field = MutableStateFlow(FollowedSeriesState(loading = true))

    private val gamesCache = mutableMapOf<Long, List<FollowedSeriesGame>>()

    /** The latest on-sale snapshot (cached on open, recomputed on pull-to-refresh). */
    private val snapshot = MutableStateFlow<Map<Long, Pair<Int, String>>>(emptyMap())

    init {
        viewModelScope.launch {
            val saved = runCatching { snapshotStore.get() }.getOrDefault(emptyList())
            val country = regionRepository.getSelectedCountryCode()
            val current = saved.filter { it.country == country }
            snapshot.value = current.associate { it.igdbGameId to (it.cutPercent to it.priceDenominated) }
            // Sales saved for another country (or before sales recorded one) are in the wrong currency: recompute.
            if (current.size < saved.size) refresh()
        }
        viewModelScope.launch {
            combine(
                followedFranchiseRepository.observeFollowed(),
                snapshot,
                collectionRepository.observeCollectionIds(),
                accountRepository.observeAuthState(),
            ) { followed, sales, ownedIds, auth -> Inputs(followed, sales, ownedIds, auth is AuthState.LoggedIn) }
                .collect { (followed, sales, ownedIds, loggedIn) ->
                    val items = followed
                        .sortedByDescending { it.addedAtMs }
                        .map { franchise ->
                            val games = gamesFor(franchise.franchiseId)
                                .map { game ->
                                    val sale = sales[game.igdbGameId]
                                    val owned = game.itadGameId != null && game.itadGameId in ownedIds
                                    game.copy(cutPercent = sale?.first, priceDenominated = sale?.second, owned = owned)
                                }
                                // Unowned first (the backlog), then on-sale (highest cut) within each group.
                                .sortedWith(compareByDescending<FollowedSeriesGame> { !it.owned }.thenByDescending { it.cutPercent ?: -1 })
                            FollowedSeriesItem(
                                franchiseId = franchise.franchiseId,
                                name = franchise.name,
                                games = games.toImmutableList(),
                                ownedCount = games.count { it.owned },
                                resolvableCount = games.count { it.itadGameId != null },
                            )
                        }
                    uiState.update { it.copy(loading = false, loggedIn = loggedIn, items = items.toImmutableList()) }
                }
        }
    }

    /** Combine carrier — keeps the 4-arg [combine] transform readable and destructurable. */
    private data class Inputs(
        val followed: List<FollowedFranchise>,
        val sales: Map<Long, Pair<Int, String>>,
        val ownedIds: Set<String>,
        val loggedIn: Boolean,
    )

    /** Pull-to-refresh: recompute the on-sale snapshot (expensive) and rewrite the cache. */
    fun refresh() {
        viewModelScope.launch {
            uiState.update { it.copy(refreshing = true) }
            val onSale = try {
                franchiseChecker.currentOnSale()
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                error(logger, t); null
            }
            if (onSale != null) {
                runCatching { snapshotStore.replace(onSale) }
                snapshot.value = onSale.associate { it.igdbGameId to (it.cutPercent to it.priceDenominated) }
            }
            uiState.update { it.copy(refreshing = false) }
        }
    }

    private suspend fun gamesFor(franchiseId: Long): List<FollowedSeriesGame> =
        gamesCache.getOrElse(franchiseId) {
            // Don't cache a transient failure: rethrow cancellation, and return (without writing the cache)
            // on a real error so a network blip doesn't pin this franchise to "0 games" for the VM's lifetime.
            val members = try {
                igdbRepository.fetchFranchiseGames(franchiseId, GAMES_PER_FRANCHISE)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                error(logger, t); return emptyList()
            }
            // Resolve each member to an ITAD id concurrently (Steam-appid bridge, 30-day cached) so we can
            // diff against the owned-games set. Non-Steam members resolve to null and are excluded from the count.
            coroutineScope {
                members.map { game ->
                    async {
                        val itadId = game.steamAppId?.let { steamId ->
                            try {
                                gamesRepository.findGameIdBySteamAppId(steamId, game.name)
                            } catch (ce: CancellationException) {
                                throw ce
                            } catch (t: Throwable) {
                                null
                            }
                        }
                        FollowedSeriesGame(
                            igdbGameId = game.id,
                            title = game.name,
                            coverUrl = game.coverImageId?.let { igdbImageUrl(it, IgdbImageSize.CoverBig) },
                            itadGameId = itadId,
                        )
                    }
                }.awaitAll()
            }.also { gamesCache[franchiseId] = it }
        }

    fun unfollow(franchiseId: Long) {
        viewModelScope.launch { followedFranchiseRepository.remove(franchiseId) }
    }

    private companion object {
        const val GAMES_PER_FRANCHISE = 20
    }
}
