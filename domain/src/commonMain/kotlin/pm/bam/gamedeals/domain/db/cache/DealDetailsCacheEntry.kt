package pm.bam.gamedeals.domain.db.cache

import androidx.room.Entity

/**
 * Room cache row for a deal-details transact read (ITAD caching strategy, Phase 3, #264).
 *
 * The nested [DealDetails][pm.bam.gamedeals.domain.models.DealDetails] model (game info + cheaper
 * stores + cheapest price, all region-priced) is stored as a serialized JSON [json] blob rather than
 * normalised into per-field tables — the surface is read/written as one unit, so a blob keeps the
 * cache simple. Keyed by `(dealId, country)` so each region's prices coexist; [expires] drives the
 * 2-hour transact TTL (fresh-blocking with serve-stale-on-error, via the repository's `CachedResource`).
 */
@Entity(tableName = "DealDetailsCache", primaryKeys = ["dealId", "country"])
data class DealDetailsCacheEntry(
    val dealId: String,
    val country: String,
    val json: String,
    val expires: Long,
)
