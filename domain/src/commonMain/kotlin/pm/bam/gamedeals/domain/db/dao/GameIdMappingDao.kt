package pm.bam.gamedeals.domain.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import pm.bam.gamedeals.domain.db.cache.GameIdMappingEntry

@Dao
internal interface GameIdMappingDao {

    /** The cached game-UUID mapping for [steamAppId], or null when not cached. */
    @Query("SELECT * FROM GameIdMapping WHERE steamAppId IS :steamAppId")
    suspend fun get(steamAppId: Int): GameIdMappingEntry?

    /** Upserts a resolved mapping (one row per `steamAppId`; misses are never stored). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: GameIdMappingEntry)

    /** Drops mappings whose long TTL expired before [threshold] — the launch eviction sweep (Phase 8). */
    @Query("DELETE FROM GameIdMapping WHERE expires < :threshold")
    suspend fun deleteExpiredBefore(threshold: Long)

    /** Clears the whole table — the `cacheSchemaVersion` bump (Phase 8 / D3 UUID-merge guard). */
    @Query("DELETE FROM GameIdMapping")
    suspend fun clear()
}
