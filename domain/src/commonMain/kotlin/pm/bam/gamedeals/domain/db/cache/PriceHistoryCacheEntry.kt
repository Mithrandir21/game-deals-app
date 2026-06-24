package pm.bam.gamedeals.domain.db.cache

import androidx.room.Entity

/**
 * Room cache row for a game's price-history series (ITAD caching strategy, Phase 4, #265).
 *
 * The whole [PriceHistory][pm.bam.gamedeals.domain.models.PriceHistory] series is stored as a serialized
 * JSON [json] blob (the chart reads the full series, so there's no per-point query to justify normalising
 * into point rows — same rationale as [DealDetailsCacheEntry]). Keyed by `(gameId, country)` so each
 * region's prices coexist (D5).
 *
 * The series is append-only, so refresh is **incremental**: a stale entry is topped-up by fetching only
 * points newer than the latest cached one and merging (D4). [expires] gates the (long) SWR refresh;
 * [fetchedAt] records the last successful refresh so the launch sweep can apply the 30-day retention rule
 * (D9, Phase 8) — "not *refreshed* in 30 days", which the latest point's timestamp alone can't express
 * (a price that simply hasn't changed in 60 days was still recently fetched).
 */
@Entity(tableName = "PriceHistoryCache", primaryKeys = ["gameId", "country"])
data class PriceHistoryCacheEntry(
    val gameId: String,
    val country: String,
    val json: String,
    val fetchedAt: Long,
    val expires: Long,
)
