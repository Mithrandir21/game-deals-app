package pm.bam.gamedeals.domain.repositories.sharedwishlist

import androidx.compose.runtime.Immutable
import kotlin.coroutines.cancellation.CancellationException
import pm.bam.gamedeals.common.exceptions.isRateLimited
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import pm.bam.gamedeals.domain.models.BundleGamePrice
import pm.bam.gamedeals.domain.models.GameArtwork
import pm.bam.gamedeals.domain.source.DealsSource
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.warn

/** One game on a friend's shared wishlist, with its current best price in the user's region (null when not on sale). */
@Immutable
data class SharedWishlistGame(
    val gameId: String,
    val title: String,
    val artwork: GameArtwork = GameArtwork(),
    val price: BundleGamePrice? = null,
)

/** A friend's wishlist as loaded: the games that could be read, in the link's order, and how many could not. */
data class SharedWishlist(
    val games: List<SharedWishlistGame>,
    val unavailableCount: Int = 0,
)

/**
 * Reads a friend's wishlist link (`ShareLinks.wishlist`) into games and prices. The link carries only game ids, so
 * each game's title and art come from ITAD (`/games/info/v2`, one call per game, at most ten) and the prices from one
 * batched `/games/prices/v3` call. Nothing about the list is stored.
 */
interface SharedWishlistRepository {
    /**
     * Loads [gameIds] (repeats dropped). A game whose info can't be read is left out and counted in
     * [SharedWishlist.unavailableCount]; prices are best-effort. Throws when no game at all could be read, with a
     * failure as the cause (a rate limit first), so callers can tell "ITAD is busy" from "offline".
     */
    suspend fun load(gameIds: List<String>): SharedWishlist
}

internal class SharedWishlistRepositoryImpl(
    private val dealsSource: DealsSource,
    private val logger: Logger,
) : SharedWishlistRepository {

    override suspend fun load(gameIds: List<String>): SharedWishlist = coroutineScope {
        val ids = gameIds.distinct()
        if (ids.isEmpty()) return@coroutineScope SharedWishlist(emptyList())
        val prices = async { fetchPrices(ids) }
        val games = ids.map { id -> async { fetchGame(id) } }.awaitAll()
        val priceById = prices.await().associateBy { it.gameId }
        val loaded = games.mapNotNull { it.getOrNull() }.map { it.copy(price = priceById[it.gameId]) }
        if (loaded.isEmpty()) {
            val failures = games.mapNotNull { it.exceptionOrNull() }
            throw IllegalStateException(
                "None of the ${ids.size} shared games could be loaded",
                failures.firstOrNull { it.isRateLimited() } ?: failures.firstOrNull(),
            )
        }
        SharedWishlist(games = loaded, unavailableCount = ids.size - loaded.size)
    }

    /** The game, success with null when it has no title, or the failure. */
    private suspend fun fetchGame(gameId: String): Result<SharedWishlistGame?> = try {
        val game = dealsSource.fetchGame(gameId)
        Result.success(
            game.title.takeIf { it.isNotBlank() }?.let { title -> SharedWishlistGame(gameId = gameId, title = title, artwork = game.artwork) },
        )
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        warn(logger, error) { "Shared wishlist game $gameId couldn't be loaded" }
        Result.failure(error)
    }

    private suspend fun fetchPrices(gameIds: List<String>): List<BundleGamePrice> = try {
        dealsSource.fetchBundleGamePrices(gameIds)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        warn(logger, error) { "Shared wishlist prices couldn't be loaded" }
        emptyList()
    }
}
