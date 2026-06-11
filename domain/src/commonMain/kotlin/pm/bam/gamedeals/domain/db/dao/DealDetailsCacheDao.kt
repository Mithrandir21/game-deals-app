package pm.bam.gamedeals.domain.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import pm.bam.gamedeals.domain.db.cache.DealDetailsCacheEntry

@Dao
internal interface DealDetailsCacheDao {

    /** The cached deal-details row for [dealId] in [country], or null when not cached. */
    @Query("SELECT * FROM DealDetailsCache WHERE dealId IS :dealId AND country IS :country")
    suspend fun get(dealId: String, country: String): DealDetailsCacheEntry?

    /** Upserts the cached deal-details row (one row per `(dealId, country)`). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: DealDetailsCacheEntry)

    /** Drops rows whose TTL expired before [threshold] — the launch eviction sweep (Phase 8). */
    @Query("DELETE FROM DealDetailsCache WHERE expires < :threshold")
    suspend fun deleteExpiredBefore(threshold: Long)

    /** Clears the whole table — the `cacheSchemaVersion` bump (Phase 8). */
    @Query("DELETE FROM DealDetailsCache")
    suspend fun clear()
}
