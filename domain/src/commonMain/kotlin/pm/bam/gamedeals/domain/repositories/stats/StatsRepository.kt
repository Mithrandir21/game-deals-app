package pm.bam.gamedeals.domain.repositories.stats

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.db.cache.StatsRankingsCacheEntry
import pm.bam.gamedeals.domain.db.dao.StatsRankingsCacheDao
import pm.bam.gamedeals.domain.models.RankedGame
import pm.bam.gamedeals.domain.repositories.cache.CachedResource
import pm.bam.gamedeals.domain.repositories.cache.decodeOffMain
import pm.bam.gamedeals.domain.repositories.cache.encodeOffMain
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
import pm.bam.gamedeals.domain.source.StatsSource
import pm.bam.gamedeals.domain.utils.millisInHour

/** Rankings are a curated feed (12-hour tier — ITAD caching strategy §4 / Phase 5c). */
internal const val STATS_RANKINGS_TTL_MILLIS = millisInHour * 12

internal const val RANKING_MOST_WAITLISTED = "most_waitlisted"
internal const val RANKING_MOST_COLLECTED = "most_collected"
internal const val RANKING_MOST_POPULAR = "most_popular"

/**
 * Global ITAD ranking stats for the Home feed (epic #219, Phase 5 — Most Waitlisted / Most Collected).
 *
 * Region-keyed read-through cache (ITAD caching strategy, Phase 5c): each ranking's fully-enriched
 * `List<RankedGame>` (incl. snapshotted price + boxart) is cached per `(rankingType, country)` at a 12h
 * feed TTL, which also removes the source's heavy per-game `/games/info` boxart fan-out on a warm cache.
 */
interface StatsRepository {
    suspend fun getMostWaitlisted(limit: Int? = null): List<RankedGame>
    suspend fun getMostCollected(limit: Int? = null): List<RankedGame>
    suspend fun getMostPopular(limit: Int? = null): List<RankedGame>
}

internal class StatsRepositoryImpl(
    private val statsSource: StatsSource,
    private val clock: Clock,
    private val regionRepository: RegionRepository,
    private val statsRankingsCacheDao: StatsRankingsCacheDao,
    private val json: Json,
) : StatsRepository {

    private val serializer = ListSerializer(RankedGame.serializer())

    override suspend fun getMostWaitlisted(limit: Int?): List<RankedGame> =
        ranking(RANKING_MOST_WAITLISTED, limit) { statsSource.fetchMostWaitlisted(it) }

    override suspend fun getMostCollected(limit: Int?): List<RankedGame> =
        ranking(RANKING_MOST_COLLECTED, limit) { statsSource.fetchMostCollected(it) }

    override suspend fun getMostPopular(limit: Int?): List<RankedGame> =
        ranking(RANKING_MOST_POPULAR, limit) { statsSource.fetchMostPopular(it) }

    /**
     * Read-through cache for one ranking. Cold fetches + caches; fresh decodes the blob; stale refetches.
     * Bounded by serve-stale-on-error (D7): a warm cache falls back to the cached ranking on a failed
     * refresh; a cold cache surfaces the failure (Home treats each section as best-effort). The result is
     * `take(limit)`-d defensively — the blob holds whatever count the ranking was last fetched at.
     */
    private suspend fun ranking(
        rankingType: String,
        limit: Int?,
        fetch: suspend (Int?) -> List<RankedGame>,
    ): List<RankedGame> {
        val country = regionRepository.getSelectedCountryCode()
        val cachedEntry = statsRankingsCacheDao.get(rankingType, country)
        var refreshed: List<RankedGame>? = null
        val cache = CachedResource(
            clock = clock,
            read = { cachedEntry?.let(::listOf) ?: emptyList() },
            expiresAtMillis = { it.expires },
            refresh = {
                val fetched = fetch(limit)
                refreshed = fetched
                val now = clock.nowMillis()
                statsRankingsCacheDao.upsert(
                    StatsRankingsCacheEntry(
                        rankingType = rankingType,
                        country = country,
                        json = json.encodeOffMain(serializer, fetched),
                        fetchedAt = now,
                        expires = now + STATS_RANKINGS_TTL_MILLIS,
                    )
                )
            },
        )
        cache.refreshIfNeeded()
        val result = refreshed ?: cachedEntry?.let { json.decodeOffMain(serializer, it.json) } ?: emptyList()
        return limit?.let(result::take) ?: result
    }
}
