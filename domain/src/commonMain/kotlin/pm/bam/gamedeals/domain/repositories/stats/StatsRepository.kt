package pm.bam.gamedeals.domain.repositories.stats

import pm.bam.gamedeals.domain.models.RankedGame
import pm.bam.gamedeals.domain.source.StatsSource

/**
 * Global ITAD ranking stats for the Home feed (epic #219, Phase 5 — Most Waitlisted / Most Collected).
 *
 * A thin seam over [StatsSource]; the source does the ITAD ranking calls and per-game price enrichment
 * (Phase 5.1, #235). Rankings are fetched fresh per call (not cached) — they're a small, region-aware,
 * Home-only feed.
 */
interface StatsRepository {
    suspend fun getMostWaitlisted(limit: Int? = null): List<RankedGame>
    suspend fun getMostCollected(limit: Int? = null): List<RankedGame>
    suspend fun getMostPopular(limit: Int? = null): List<RankedGame>
}

internal class StatsRepositoryImpl(
    private val statsSource: StatsSource,
) : StatsRepository {
    override suspend fun getMostWaitlisted(limit: Int?): List<RankedGame> = statsSource.fetchMostWaitlisted(limit)
    override suspend fun getMostCollected(limit: Int?): List<RankedGame> = statsSource.fetchMostCollected(limit)
    override suspend fun getMostPopular(limit: Int?): List<RankedGame> = statsSource.fetchMostPopular(limit)
}
