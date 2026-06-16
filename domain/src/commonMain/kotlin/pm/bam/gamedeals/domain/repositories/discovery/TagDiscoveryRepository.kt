package pm.bam.gamedeals.domain.repositories.discovery

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.db.cache.IgdbTagEntry
import pm.bam.gamedeals.domain.db.dao.IgdbTagDao
import pm.bam.gamedeals.domain.models.BundleGamePrice
import pm.bam.gamedeals.domain.models.IgdbImageSize
import pm.bam.gamedeals.domain.models.IgdbTag
import pm.bam.gamedeals.domain.models.IgdbTagDimension
import pm.bam.gamedeals.domain.models.IgdbTagFilter
import pm.bam.gamedeals.domain.models.TagDiscoveryResult
import pm.bam.gamedeals.domain.models.igdbImageUrl
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.igdb.IgdbRepository
import pm.bam.gamedeals.domain.utils.millisInDay
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.debug

/** One discovery result page (mirrors the deals feed page size). */
const val DISCOVERY_PAGE_SIZE = 30

/** Tag vocabulary is near-static, so a long TTL is plenty — a monthly self-heal picks up IGDB changes. */
internal const val IGDB_TAG_VOCABULARY_TTL_MILLIS = millisInDay * 30

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
    private val igdbTagDao: IgdbTagDao,
    private val clock: Clock,
) : TagDiscoveryRepository {

    /**
     * Reads the cached vocabulary; on a cold/expired cache it refetches from IGDB and replaces the
     * table. If the refetch fails but a (stale) cache exists, the stale vocabulary is served rather
     * than failing the picker — the vocabulary is near-static, so stale is fine.
     */
    override suspend fun getTagVocabulary(): List<IgdbTag> {
        val now = clock.nowMillis()
        val cached = igdbTagDao.getAll()
        if (cached.isNotEmpty() && cached.all { it.expires > now }) {
            return cached.mapNotNull { it.toIgdbTagOrNull() }
        }
        return runCatching {
            val fetched = igdbRepository.fetchTagVocabulary() + igdbRepository.fetchCuratedKeywords(CURATED_KEYWORD_SLUGS)
            if (fetched.isNotEmpty()) {
                val expires = now + IGDB_TAG_VOCABULARY_TTL_MILLIS
                igdbTagDao.clear()
                igdbTagDao.upsertAll(fetched.map { it.toEntry(expires) })
            }
            fetched
        }.getOrElse { error ->
            if (cached.isNotEmpty()) cached.mapNotNull { it.toIgdbTagOrNull() } else throw error
        }
    }

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

    private fun IgdbTag.toEntry(expires: Long): IgdbTagEntry =
        IgdbTagEntry(dimension = dimension.name, igdbId = igdbId, name = name, slug = slug, expires = expires)

    // Drops a row whose persisted dimension no longer maps to a known enum value (defensive — survives
    // an enum rename without crashing the picker; the row is simply re-fetched on the next refresh).
    private fun IgdbTagEntry.toIgdbTagOrNull(): IgdbTag? {
        val dim = IgdbTagDimension.entries.firstOrNull { it.name == dimension } ?: return null
        return IgdbTag(dimension = dim, igdbId = igdbId, name = name, slug = slug)
    }
}
