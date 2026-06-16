package pm.bam.gamedeals.domain.repositories.discovery

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import pm.bam.gamedeals.domain.models.BundleGamePrice
import pm.bam.gamedeals.domain.models.IgdbImageSize
import pm.bam.gamedeals.domain.models.IgdbTag
import pm.bam.gamedeals.domain.models.IgdbTagFilter
import pm.bam.gamedeals.domain.models.TagDiscoveryResult
import pm.bam.gamedeals.domain.models.igdbImageUrl
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.igdb.IgdbRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.debug

/** One discovery result page (mirrors the deals feed page size). */
const val DISCOVERY_PAGE_SIZE = 30

interface TagDiscoveryRepository {

    /**
     * The curated tag-picker vocabulary (epic #307): genres + themes + game-modes + player
     * perspectives + the curated keyword allow-list, as one flat dimension-tagged list.
     */
    suspend fun getTagVocabulary(): List<IgdbTag>

    /**
     * One offset page of priced discovery results for an AND-combined tag [filter]. Returns an empty
     * list for an empty filter. Pricing is resolved for the visible page only (one ITAD lookup per
     * game with a Steam app id, then a single batched price query).
     */
    suspend fun discover(filter: IgdbTagFilter, offset: Int, pageSize: Int = DISCOVERY_PAGE_SIZE): List<TagDiscoveryResult>
}

internal class TagDiscoveryRepositoryImpl(
    private val logger: Logger,
    private val igdbRepository: IgdbRepository,
    private val gamesRepository: GamesRepository,
) : TagDiscoveryRepository {

    override suspend fun getTagVocabulary(): List<IgdbTag> =
        igdbRepository.fetchTagVocabulary() + igdbRepository.fetchCuratedKeywords(CURATED_KEYWORD_SLUGS)

    override suspend fun discover(filter: IgdbTagFilter, offset: Int, pageSize: Int): List<TagDiscoveryResult> = coroutineScope {
        if (filter.isEmpty()) return@coroutineScope emptyList()

        val games = igdbRepository.fetchGamesByTags(filter, limit = pageSize, offset = offset)
        debug(logger) { "Tag discovery: ${games.size} IGDB games at offset $offset" }
        if (games.isEmpty()) return@coroutineScope emptyList()

        // Resolve ITAD ids for THIS page only — one /games/lookup per game that has a Steam app id, run
        // concurrently (the ITAD client's own concurrency limiter + 429 retry throttle the fan-out) and
        // 30-day-cached. Games without a Steam app id skip the lookup entirely.
        val itadIdByIgdbId: Map<Long, String?> = games
            .map { game ->
                async {
                    game.id to game.steamAppId?.let { gamesRepository.findGameIdBySteamAppId(it, game.name) }
                }
            }
            .awaitAll()
            .toMap()

        // Batch-price all resolved ITAD ids in a single /games/prices call.
        val resolvedIds = itadIdByIgdbId.values.filterNotNull().distinct()
        val priceByItadId: Map<String, BundleGamePrice> =
            if (resolvedIds.isEmpty()) emptyMap()
            else gamesRepository.getGamePrices(resolvedIds).associateBy { it.gameId }

        games.map { game ->
            val itadId = itadIdByIgdbId[game.id]
            val pricing = when {
                // Tracked on ITAD → in-app Game Page (price may be null when there's no current deal).
                itadId != null -> TagDiscoveryResult.Pricing.Priced(itadId, priceByItadId[itadId])
                // Has a Steam app id but ITAD doesn't track it → link out to the Steam store.
                game.steamAppId != null -> TagDiscoveryResult.Pricing.SteamLinkOut(steamStoreUrl(game.steamAppId))
                // No Steam app id (console/mobile-only) → shown, nothing to open.
                else -> TagDiscoveryResult.Pricing.Unpriced
            }
            TagDiscoveryResult(
                igdbId = game.id,
                title = game.name,
                coverImageUrl = game.coverImageId?.let { igdbImageUrl(it, IgdbImageSize.CoverBig) },
                steamAppId = game.steamAppId,
                pricing = pricing,
            )
        }
    }

    private fun steamStoreUrl(steamAppId: Int): String = "https://store.steampowered.com/app/$steamAppId"
}
