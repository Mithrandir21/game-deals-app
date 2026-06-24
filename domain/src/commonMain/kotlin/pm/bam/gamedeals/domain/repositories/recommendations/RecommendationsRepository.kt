package pm.bam.gamedeals.domain.repositories.recommendations

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import pm.bam.gamedeals.domain.models.IgdbGame
import pm.bam.gamedeals.domain.repositories.collection.CollectionRepository
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.igdb.IgdbRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository

/**
 * "For You" recommendations (#6): games similar to what the user wants or owns. Seeds from the user's
 * waitlist (active buying intent) + collection, resolves each seed to its IGDB record via the existing
 * Steam-appid bridge, and unions their IGDB `similar_games`. Deduped; the seeds themselves are excluded.
 *
 * Best-effort throughout — a failed seed contributes nothing rather than failing the feed; an empty
 * library (or logged-out user with no synced ids) yields an empty list, so the Home strip simply hides.
 * Rendered as navigable tiles whose tap resolves price on the game page, so no IGDB→ITAD price bridge
 * is needed here.
 */
interface RecommendationsRepository {
    suspend fun getRecommendations(limit: Int = DEFAULT_LIMIT): List<IgdbGame.IgdbSimilarGame>

    companion object {
        const val DEFAULT_LIMIT = 12
    }
}

internal class RecommendationsRepositoryImpl(
    private val waitlistRepository: WaitlistRepository,
    private val collectionRepository: CollectionRepository,
    private val gamesRepository: GamesRepository,
    private val igdbRepository: IgdbRepository,
) : RecommendationsRepository {

    override suspend fun getRecommendations(limit: Int): List<IgdbGame.IgdbSimilarGame> = coroutineScope {
        val waitlist = runCatching { waitlistRepository.getWaitlist() }.getOrDefault(emptyList())
        val collection = runCatching { collectionRepository.getCollection() }.getOrDefault(emptyList())

        // Waitlist first (active intent), then collection; cap the fan-out so we don't issue N round-trips.
        val seedIds = (waitlist.map { it.gameId } + collection.map { it.gameId }).distinct().take(MAX_SEEDS)
        if (seedIds.isEmpty()) return@coroutineScope emptyList()

        // The seeds are ITAD ids and recommendations are IGDB ids (different spaces), so an exact owned-
        // filter isn't possible; drop obvious self-matches by (case-insensitive) title instead.
        val ownedTitles = (waitlist.map { it.title } + collection.map { it.title }).map { it.lowercase() }.toSet()

        seedIds
            .map { seedId -> async { runCatching { similarFor(seedId) }.getOrDefault(emptyList()) } }
            .awaitAll()
            .flatten()
            .distinctBy { it.id }
            .filter { it.name.lowercase() !in ownedTitles }
            .take(limit)
    }

    // Resolve one ITAD seed to its IGDB similar games via the Steam-appid bridge (game details → IGDB).
    private suspend fun similarFor(seedGameId: String): List<IgdbGame.IgdbSimilarGame> {
        val steamAppId = gamesRepository.getGameDetails(seedGameId).info.steamAppID ?: return emptyList()
        return igdbRepository.fetchGameDetailsBySteamId(steamAppId)?.similarGames ?: emptyList()
    }

    private companion object {
        const val MAX_SEEDS = 4
    }
}
