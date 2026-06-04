package pm.bam.gamedeals.domain.repositories.stats

import pm.bam.gamedeals.domain.models.RankedGame

/**
 * Global ITAD ranking stats for the Home feed (epic #219, Phase 5 — Most Waitlisted / Most Collected).
 *
 * Phase 0 STUB: returns empty lists until the live stats source + price enrichment land in Phase 5.1
 * (#235). Registered in DI now so the Home feed has a stable seam to inject.
 */
interface StatsRepository {
    suspend fun getMostWaitlisted(limit: Int? = null): List<RankedGame>
    suspend fun getMostCollected(limit: Int? = null): List<RankedGame>
    suspend fun getMostPopular(limit: Int? = null): List<RankedGame>
}

internal class StatsRepositoryImpl : StatsRepository {
    override suspend fun getMostWaitlisted(limit: Int?): List<RankedGame> = emptyList()
    override suspend fun getMostCollected(limit: Int?): List<RankedGame> = emptyList()
    override suspend fun getMostPopular(limit: Int?): List<RankedGame> = emptyList()
}
