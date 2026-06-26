package pm.bam.gamedeals.domain.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import pm.bam.gamedeals.domain.db.cache.RecentlyViewedGameEntry

@Dao
internal interface RecentlyViewedDao {

    /** Most-recent-first, capped to [limit] (the carousel reads this). */
    @Query("SELECT * FROM RecentlyViewedGame ORDER BY viewedAtEpochMs DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<RecentlyViewedGameEntry>>

    /** Upsert on the gameId PK — re-viewing a game replaces the row (moving it to the top via its new timestamp). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: RecentlyViewedGameEntry)

    /** Trims everything beyond the newest [keep] rows, so the table never grows unbounded. */
    @Query(
        "DELETE FROM RecentlyViewedGame WHERE gameId NOT IN " +
            "(SELECT gameId FROM RecentlyViewedGame ORDER BY viewedAtEpochMs DESC LIMIT :keep)"
    )
    suspend fun trimTo(keep: Int)

    @Query("DELETE FROM RecentlyViewedGame WHERE gameId IS :gameId")
    suspend fun delete(gameId: String)

    @Query("DELETE FROM RecentlyViewedGame")
    suspend fun clear()
}
