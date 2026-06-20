package pm.bam.gamedeals.domain.repositories.franchise

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import pm.bam.gamedeals.domain.models.FollowedFranchise
import pm.bam.gamedeals.domain.models.FranchiseSaleGame
import pm.bam.gamedeals.domain.models.IgdbGame
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.igdb.IgdbRepository
import pm.bam.gamedeals.domain.repositories.notifications.PendingNotificationAlert

/**
 * The client-side half of followed-franchise notifications: for each franchise the user follows, fetch its
 * games, resolve them to the user's region prices through the existing IGDB→ITAD Steam-appid bridge, and
 * compute which are on sale. Driven by the same background poll as the ITAD notification sync, so it inherits
 * the user's opt-in + schedule.
 *
 * Reuses the same recipe as tag discovery's `trackedResults`: drop games without a Steam app id, resolve the
 * rest to ITAD ids concurrently (30-day cached lookups), then a single batched price query. Unlike the ITAD
 * waitlist path this is **not** login-gated — following works logged-out, so these alerts do too.
 *
 * Two consumers of the same on-sale computation:
 *  - [collectCrossedAlerts] emits a [PendingNotificationAlert] only for *newly* on-sale games (deduped via
 *    [FollowedDealSeenStore], signature = `gameId@price`), and persists the **full** on-sale set to
 *    [FranchiseSaleSnapshotStore] so the in-app Followed-series screen can surface current sales.
 *  - [currentOnSale] returns the full current set on demand (the screen's pull-to-refresh).
 *
 * Best-effort throughout — a failed franchise (IGDB/ITAD error) contributes nothing rather than failing.
 */
interface FollowedFranchiseChecker {
    suspend fun collectCrossedAlerts(): List<PendingNotificationAlert>

    /** The full set of games currently on sale across followed franchises (no persistence/dedup). */
    suspend fun currentOnSale(): List<FranchiseSaleGame>
}

internal class FollowedFranchiseCheckerImpl(
    private val followedFranchiseRepository: FollowedFranchiseRepository,
    private val igdbRepository: IgdbRepository,
    private val gamesRepository: GamesRepository,
    private val seenStore: FollowedDealSeenStore,
    private val snapshotStore: FranchiseSaleSnapshotStore,
    private val franchiseAlertTitle: (gameTitle: String, franchiseName: String, cutPercent: Int, priceDenominated: String) -> String,
) : FollowedFranchiseChecker, FranchiseFollowSeeder {

    override suspend fun collectCrossedAlerts(): List<PendingNotificationAlert> {
        val onSale = currentOnSale()
        // Persist the full snapshot every poll (regardless of deltas) so the Followed-series screen always
        // has fresh "current sales" to render. Best-effort — a write failure must not fail the poll.
        runCatching { snapshotStore.replace(onSale) }

        val seen = seenStore.get()
        val currentSignatures = onSale.map { it.signature }.toSet()
        val new = onSale.filter { it.signature !in seen }.distinctBy { it.signature }
        // Prune the remembered set to what's on sale right now, so an ended-then-returned deal re-alerts.
        seenStore.replace(currentSignatures)

        return new.map { game ->
            PendingNotificationAlert(
                notificationId = "$FOLLOWED_DEAL_ID_PREFIX${game.signature}",
                title = franchiseAlertTitle(game.title, game.franchiseName, game.cutPercent, game.priceDenominated),
                games = emptyList(),
                gameId = game.itadGameId,
            )
        }
    }

    override suspend fun currentOnSale(): List<FranchiseSaleGame> = coroutineScope {
        // Newest-followed first, bounded — a heavily-followed user can't fan out into hundreds of round-trips.
        val followed = followedFranchiseRepository.getFollowed()
            .sortedByDescending { it.addedAtMs }
            .take(MAX_FOLLOWED_CHECKED)
        if (followed.isEmpty()) return@coroutineScope emptyList()

        followed
            .map { franchise -> async { runCatching { onSaleGamesFor(franchise) }.getOrDefault(emptyList()) } }
            .awaitAll()
            .flatten()
    }

    override suspend fun seedSeen(franchiseId: Long) {
        // Use the real follow (for name) if present, else a minimal stand-in — only the signature matters here.
        val franchise = followedFranchiseRepository.getFollowed().firstOrNull { it.franchiseId == franchiseId }
            ?: FollowedFranchise(franchiseId, "", 0L)
        val signatures = runCatching { onSaleGamesFor(franchise) }.getOrDefault(emptyList())
            .map { it.signature }
            .toSet()
        if (signatures.isNotEmpty()) {
            // Merge (not replace): other franchises' already-seen signatures must survive.
            runCatching { seenStore.replace(seenStore.get() + signatures) }
        }
    }

    private suspend fun onSaleGamesFor(franchise: FollowedFranchise): List<FranchiseSaleGame> = coroutineScope {
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
            if (cut <= 0) return@mapNotNull null // a 0% "deal" isn't a sale worth surfacing
            FranchiseSaleGame(
                franchiseId = franchise.franchiseId,
                franchiseName = franchise.name,
                igdbGameId = game.id,
                itadGameId = itadId,
                title = game.name,
                cutPercent = cut,
                priceValue = priceValue,
                priceDenominated = price.bestPriceDenominated.orEmpty(),
            )
        }
    }

    private companion object {
        const val MAX_FOLLOWED_CHECKED = 10
        const val FRANCHISE_GAMES_LIMIT = 30
        const val FOLLOWED_DEAL_ID_PREFIX = "followeddeal:"
    }
}
