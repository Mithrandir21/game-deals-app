package pm.bam.gamedeals.domain.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import pm.bam.gamedeals.domain.db.cache.IgdbTagEntry

@Dao
internal interface IgdbTagDao {

    /** The whole cached tag vocabulary (all dimensions). */
    @Query("SELECT * FROM IgdbTag")
    suspend fun getAll(): List<IgdbTagEntry>

    /** Replaces the cached vocabulary in bulk (one upsert per refresh). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<IgdbTagEntry>)

    /** Drops vocabulary whose TTL expired before [threshold] — the launch eviction sweep (Phase 8). */
    @Query("DELETE FROM IgdbTag WHERE expires < :threshold")
    suspend fun deleteExpiredBefore(threshold: Long)

    /** Clears the whole table before a fresh write. */
    @Query("DELETE FROM IgdbTag")
    suspend fun clear()
}
