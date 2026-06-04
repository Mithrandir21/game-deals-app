package pm.bam.gamedeals.domain.source

import pm.bam.gamedeals.domain.models.RankedGame

/**
 * Global ITAD ranking stats (epic #219, Phase 5). API-key only. The live implementation lands in
 * `:remote:itad` (Phase 5.1, #235) and enriches each ranked game with its current best price.
 */
interface StatsSource {
    suspend fun fetchMostWaitlisted(limit: Int? = null): List<RankedGame>
    suspend fun fetchMostCollected(limit: Int? = null): List<RankedGame>
    suspend fun fetchMostPopular(limit: Int? = null): List<RankedGame>
}
