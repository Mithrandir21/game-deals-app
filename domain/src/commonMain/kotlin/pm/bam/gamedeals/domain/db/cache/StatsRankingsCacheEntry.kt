package pm.bam.gamedeals.domain.db.cache

import androidx.room.Entity

/**
 * Room cache row for one ITAD global ranking (ITAD caching strategy, Phase 5c, #266).
 *
 * The ranking's whole `List<RankedGame>` (ids + title + boxart + snapshotted price) is stored as a
 * serialized JSON [json] blob. Keyed by [rankingType] (`most_waitlisted` / `most_collected` /
 * `most_popular`) and [country] — rankings are region-scoped via their price/boxart enrichment.
 * [expires] gates the 12-hour feed-tier SWR refresh; [fetchedAt] records the last successful refresh
 * for the launch retention sweep (D9, Phase 8).
 */
@Entity(tableName = "StatsRankingsCache", primaryKeys = ["rankingType", "country"])
data class StatsRankingsCacheEntry(
    val rankingType: String,
    val country: String,
    val json: String,
    val fetchedAt: Long,
    val expires: Long,
)
