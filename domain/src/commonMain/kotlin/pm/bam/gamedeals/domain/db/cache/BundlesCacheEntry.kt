package pm.bam.gamedeals.domain.db.cache

import androidx.room.Entity

/**
 * Room cache row for a region's storefront bundles (ITAD caching strategy, Phase 5b, #266).
 *
 * The whole `List<Bundle>` for a region is stored as a single serialized JSON [json] blob (the bundles
 * screen and the detail screen both read the full list; there's no per-bundle query to justify
 * normalising — same rationale as the other blob caches). Keyed by [country] alone (bundles are
 * region-scoped but not otherwise sub-keyed); [expires] gates the 12-hour feed-tier SWR refresh and
 * [fetchedAt] records the last successful refresh for the launch retention sweep (D9, Phase 8).
 */
@Entity(tableName = "BundlesCache", primaryKeys = ["country"])
data class BundlesCacheEntry(
    val country: String,
    val json: String,
    val fetchedAt: Long,
    val expires: Long,
)
