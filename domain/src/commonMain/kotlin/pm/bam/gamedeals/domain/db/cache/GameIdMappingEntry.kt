package pm.bam.gamedeals.domain.db.cache

import androidx.room.Entity

/**
 * Room cache row for the Steam-appID → ITAD game-UUID identity mapping (ITAD caching strategy, Phase 6,
 * #267).
 *
 * Keyed by [steamAppId] alone — a Steam appID maps to the same ITAD game globally, so this mapping is
 * **region-invariant** (no `country` dimension). [expires] is a long TTL (not literal): the mapping is
 * stable, but ITAD UUIDs can rarely merge, so it self-heals on TTL and is also cleared by the
 * `cacheSchemaVersion` guard (D3 / Phase 8). Lookup *misses* are deliberately not stored (D3), so this
 * table holds only resolved mappings — hence no `fetchedAt` and no retention sweep (D9).
 */
@Entity(tableName = "GameIdMapping", primaryKeys = ["steamAppId"])
data class GameIdMappingEntry(
    val steamAppId: Int,
    val gameId: String,
    val expires: Long,
)
