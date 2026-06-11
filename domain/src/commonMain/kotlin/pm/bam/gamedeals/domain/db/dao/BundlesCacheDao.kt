package pm.bam.gamedeals.domain.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import pm.bam.gamedeals.domain.db.cache.BundlesCacheEntry

@Dao
internal interface BundlesCacheDao {

    /** The cached bundles row for [country], or null when not cached. */
    @Query("SELECT * FROM BundlesCache WHERE country IS :country")
    suspend fun get(country: String): BundlesCacheEntry?

    /** Upserts the cached bundles row (one row per `country`). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: BundlesCacheEntry)

    /** Drops rows whose TTL expired before [threshold] — the launch eviction sweep (Phase 8). */
    @Query("DELETE FROM BundlesCache WHERE expires < :threshold")
    suspend fun deleteExpiredBefore(threshold: Long)

    /** Clears the whole table — the `cacheSchemaVersion` bump (Phase 8). */
    @Query("DELETE FROM BundlesCache")
    suspend fun clear()
}
