package pm.bam.gamedeals.domain.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import pm.bam.gamedeals.domain.db.cache.StatsRankingsCacheEntry

@Dao
internal interface StatsRankingsCacheDao {

    /** The cached ranking row for [rankingType] in [country], or null when not cached. */
    @Query("SELECT * FROM StatsRankingsCache WHERE rankingType IS :rankingType AND country IS :country")
    suspend fun get(rankingType: String, country: String): StatsRankingsCacheEntry?

    /** Upserts the cached ranking row (one row per `(rankingType, country)`). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: StatsRankingsCacheEntry)

    /** Drops rows whose TTL expired before [threshold] — the launch eviction sweep (Phase 8). */
    @Query("DELETE FROM StatsRankingsCache WHERE expires < :threshold")
    suspend fun deleteExpiredBefore(threshold: Long)

    /** Clears the whole table — the `cacheSchemaVersion` bump (Phase 8). */
    @Query("DELETE FROM StatsRankingsCache")
    suspend fun clear()
}
