package pm.bam.gamedeals.domain.repositories.franchise

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import pm.bam.gamedeals.domain.models.FollowedFranchise
import pm.bam.gamedeals.domain.models.IgdbGame
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.igdb.IgdbRepository
import pm.bam.gamedeals.domain.repositories.notifications.PendingNotificationAlert

/**
 * The client-side half of followed-franchise notifications: for each franchise the user follows, fetch its
 * games, resolve them to the user's region prices through the existing IGDB→ITAD Steam-appid bridge, and
 * emit a [PendingNotificationAlert] for every game that is *newly* on sale. Driven by the same background
 * poll as the ITAD notification sync, so it inherits the user's opt-in + schedule.
 *
 * Reuses the same recipe as tag discovery's `trackedResults`: drop games without a Steam app id, resolve
 * the rest to ITAD ids concurrently (30-day cached lookups), then a single batched price query. Unlike the
 * ITAD waitlist path this is **not** login-gated — following works logged-out, so these alerts do too.
 *
 * Dedupe is delegated to [FollowedDealSeenStore] (signature = `gameId@price`): a deal at a price already
 * surfaced is suppressed, while a returned or deeper deal re-alerts. Best-effort throughout — a failed
 * franchise (IGDB/ITAD error) contributes nothing rather than failing the poll.
 */
interface FollowedFranchiseChecker {
    suspend fun collectCrossedAlerts(): List<PendingNotificationAlert>
}

internal class FollowedFranchiseCheckerImpl(
    private val followedFranchiseRepository: FollowedFranchiseRepository,
    private val igdbRepository: IgdbRepository,
    private val gamesRepository: GamesRepository,
    private val seenStore: FollowedDealSeenStore,
    private val franchiseAlertTitle: (gameTitle: String, franchiseName: String, cutPercent: Int, priceDenominated: String) -> String,
) : FollowedFranchiseChecker {

    override suspend fun collectCrossedAlerts(): List<PendingNotificationAlert> = coroutineScope {
        // Newest-followed first, bounded — a heavily-followed user can't fan out into hundreds of round-trips.
        val followed = followedFranchiseRepository.getFollowed()
            .sortedByDescending { it.addedAtMs }
            .take(MAX_FOLLOWED_CHECKED)
        if (followed.isEmpty()) return@coroutineScope emptyList()

        val onSale = followed
            .map { franchise -> async { runCatching { onSaleGamesFor(franchise) }.getOrDefault(emptyList()) } }
            .awaitAll()
            .flatten()

        val seen = seenStore.get()
        val currentSignatures = onSale.map { it.signature }.toSet()
        val new = onSale.filter { it.signature !in seen }.distinctBy { it.signature }
        // Prune the remembered set to what's on sale right now, so an ended-then-returned deal re-alerts.
        seenStore.replace(currentSignatures)

        new.map { game ->
            PendingNotificationAlert(
                notificationId = "$FOLLOWED_DEAL_ID_PREFIX${game.signature}",
                title = franchiseAlertTitle(game.title, game.franchiseName, game.cutPercent, game.priceDenominated),
                games = emptyList(),
                gameId = game.itadGameId,
            )
        }
    }

    private suspend fun onSaleGamesFor(franchise: FollowedFranchise): List<OnSaleGame> = coroutineScope {
        val games = igdbRepository.fetchFranchiseGames(franchise.franchiseId, FRANCHISE_GAMES_LIMIT)

        // Drop untracked games up front — no Steam app id means no possible ITAD lookup.
        val withSteamId = games.filter { it.steamAppId != null }
        if (withSteamId.isEmpty()) return@coroutineScope emptyList()

        // Resolve ITAD ids concurrently (cached + rate-limited in the client), keeping only the resolved.
        val tracked: List<Pair<IgdbGame, String>> = withSteamId
            .map { game -> async { game to gamesRepository.findGameIdBySteamAppId(game.steamAppId!!, game.name) } }
            .awaitAll()
            .mapNotNull { (game, itadId) -> itadId?.let { game to it } }
        if (tracked.isEmpty()) return@coroutineScope emptyList()

        val priceByItadId = gamesRepository.getGamePrices(tracked.map { it.second }.distinct())
            .associateBy { it.gameId }

        tracked.mapNotNull { (game, itadId) ->
            val price = priceByItadId[itadId] ?: return@mapNotNull null
            val cut = price.bestCutPercent ?: return@mapNotNull null // null best fields ⇒ no current deal
            val priceValue = price.bestPriceValue ?: return@mapNotNull null
            if (cut <= 0) return@mapNotNull null // a 0% "deal" isn't a sale worth a notification
            OnSaleGame(
                itadGameId = itadId,
                title = game.name,
                franchiseName = franchise.name,
                cutPercent = cut,
                priceValue = priceValue,
                priceDenominated = price.bestPriceDenominated.orEmpty(),
            )
        }
    }

    private data class OnSaleGame(
        val itadGameId: String,
        val title: String,
        val franchiseName: String,
        val cutPercent: Int,
        val priceValue: Double,
        val priceDenominated: String,
    ) {
        // Price-keyed so a still-running deal stays suppressed but a returned/deeper one re-alerts.
        val signature: String get() = "$itadGameId@$priceValue"
    }

    private companion object {
        const val MAX_FOLLOWED_CHECKED = 10
        const val FRANCHISE_GAMES_LIMIT = 30
        const val FOLLOWED_DEAL_ID_PREFIX = "followeddeal:"
    }
}
