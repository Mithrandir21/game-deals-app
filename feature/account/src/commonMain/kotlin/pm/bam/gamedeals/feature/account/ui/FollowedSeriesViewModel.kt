package pm.bam.gamedeals.feature.account.ui

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pm.bam.gamedeals.domain.models.IgdbImageSize
import pm.bam.gamedeals.domain.models.igdbImageUrl
import pm.bam.gamedeals.domain.repositories.franchise.FollowedFranchiseRepository
import pm.bam.gamedeals.domain.repositories.igdb.IgdbRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.fatal

/** One game inside a followed series — its IGDB id (for navigation) + cover (for the tile). */
@Immutable
internal data class FollowedSeriesGame(
    val igdbGameId: Long,
    val title: String,
    val coverUrl: String?,
)

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
    val items: ImmutableList<FollowedSeriesItem> = persistentListOf(),
)

/**
 * Backs the Followed-series sub-screen (#7): observes the local follows and resolves each franchise's games
 * from IGDB so the user can browse and unfollow them outside a single game page. Per-franchise game lists
 * are cached in memory so unfollowing one (which re-emits the follow list) doesn't re-fetch the others.
 */
internal class FollowedSeriesViewModel(
    private val followedFranchiseRepository: FollowedFranchiseRepository,
    private val igdbRepository: IgdbRepository,
    private val logger: Logger,
) : ViewModel() {

    val uiState: StateFlow<FollowedSeriesState>
        field = MutableStateFlow(FollowedSeriesState(loading = true))

    private val gamesCache = mutableMapOf<Long, ImmutableList<FollowedSeriesGame>>()

    init {
        viewModelScope.launch {
            followedFranchiseRepository.observeFollowed().collect { followed ->
                val items = followed
                    .sortedByDescending { it.addedAtMs }
                    .map { franchise ->
                        FollowedSeriesItem(
                            franchiseId = franchise.franchiseId,
                            name = franchise.name,
                            games = gamesFor(franchise.franchiseId),
                        )
                    }
                uiState.update { it.copy(loading = false, items = items.toImmutableList()) }
            }
        }
    }

    private suspend fun gamesFor(franchiseId: Long): ImmutableList<FollowedSeriesGame> =
        gamesCache.getOrElse(franchiseId) {
            runCatching { igdbRepository.fetchFranchiseGames(franchiseId, GAMES_PER_FRANCHISE) }
                .getOrElse { fatal(logger, it); emptyList() }
                .map { game ->
                    FollowedSeriesGame(
                        igdbGameId = game.id,
                        title = game.name,
                        coverUrl = game.coverImageId?.let { igdbImageUrl(it, IgdbImageSize.CoverBig) },
                    )
                }
                .toImmutableList()
                .also { gamesCache[franchiseId] = it }
        }

    fun unfollow(franchiseId: Long) {
        viewModelScope.launch { followedFranchiseRepository.remove(franchiseId) }
    }

    private companion object {
        const val GAMES_PER_FRANCHISE = 20
    }
}
