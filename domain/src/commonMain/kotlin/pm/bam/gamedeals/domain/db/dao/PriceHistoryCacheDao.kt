package pm.bam.gamedeals.domain.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import pm.bam.gamedeals.domain.db.cache.PriceHistoryCacheEntry

@Dao
internal interface PriceHistoryCacheDao {

    /** The cached price-history series for [gameId] in [country], or null when not cached. */
    @Query("SELECT * FROM PriceHistoryCache WHERE gameId IS :gameId AND country IS :country")
    suspend fun get(gameId: String, country: String): PriceHistoryCacheEntry?

    /** Upserts the cached price-history series (one row per `(gameId, country)`). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: PriceHistoryCacheEntry)

    /** Drops series not refreshed since [threshold] — the 30-day price-history retention (Phase 8 / D9). */
    @Query("DELETE FROM PriceHistoryCache WHERE fetchedAt < :threshold")
    suspend fun deleteFetchedBefore(threshold: Long)

    /** Clears the whole table — the `cacheSchemaVersion` bump (Phase 8). */
    @Query("DELETE FROM PriceHistoryCache")
    suspend fun clear()
}
