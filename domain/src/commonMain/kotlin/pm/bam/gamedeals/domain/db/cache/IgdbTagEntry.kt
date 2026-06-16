package pm.bam.gamedeals.domain.db.cache

import androidx.room.Entity

/**
 * Room cache row for the tag-picker vocabulary (epic #307). The IGDB genre/theme/mode/perspective
 * enums plus the curated keyword allow-list are near-static, so caching them lets the picker open
 * instantly and keeps the IGDB vocabulary endpoints off the hot path.
 *
 * Keyed by [dimension] + [igdbId] — IGDB ids are only unique within a dimension. [dimension] stores
 * the [pm.bam.gamedeals.domain.models.IgdbTagDimension] name. [expires] is a long TTL (the vocabulary
 * rarely changes); the whole table is refreshed as a unit when stale.
 */
@Entity(tableName = "IgdbTag", primaryKeys = ["dimension", "igdbId"])
data class IgdbTagEntry(
    val dimension: String,
    val igdbId: Long,
    val name: String,
    val slug: String?,
    val expires: Long,
)
