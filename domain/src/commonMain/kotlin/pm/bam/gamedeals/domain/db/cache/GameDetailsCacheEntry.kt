package pm.bam.gamedeals.domain.db.cache

import androidx.room.Entity

/**
 * Room cache row for a game-details transact read (ITAD caching strategy, Phase 3, #264).
 *
 * The nested [GameDetails][pm.bam.gamedeals.domain.models.GameDetails] model (info + cheapest price
 * ever + region-priced deals) is stored as a serialized JSON [json] blob (see
 * [DealDetailsCacheEntry] for the rationale). Keyed by `(gameId, country)`; [expires] drives the
 * 2-hour transact TTL (fresh-blocking with serve-stale-on-error, via the repository's `CachedResource`).
 */
@Entity(tableName = "GameDetailsCache", primaryKeys = ["gameId", "country"])
data class GameDetailsCacheEntry(
    val gameId: String,
    val country: String,
    val json: String,
    val expires: Long,
)
