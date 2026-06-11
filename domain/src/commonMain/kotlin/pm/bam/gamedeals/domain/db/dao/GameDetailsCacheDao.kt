package pm.bam.gamedeals.domain.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import pm.bam.gamedeals.domain.db.cache.GameDetailsCacheEntry

@Dao
internal interface GameDetailsCacheDao {

    /** The cached game-details row for [gameId] in [country], or null when not cached. */
    @Query("SELECT * FROM GameDetailsCache WHERE gameId IS :gameId AND country IS :country")
    suspend fun get(gameId: String, country: String): GameDetailsCacheEntry?

    /** Upserts the cached game-details row (one row per `(gameId, country)`). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: GameDetailsCacheEntry)

    /** Drops rows whose TTL expired before [threshold] — the launch eviction sweep (Phase 8). */
    @Query("DELETE FROM GameDetailsCache WHERE expires < :threshold")
    suspend fun deleteExpiredBefore(threshold: Long)

    /** Clears the whole table — the `cacheSchemaVersion` bump (Phase 8). */
    @Query("DELETE FROM GameDetailsCache")
    suspend fun clear()
}
