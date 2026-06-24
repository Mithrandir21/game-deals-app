package pm.bam.gamedeals.domain.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import pm.bam.gamedeals.domain.db.cache.IgnoredGameIdEntry

@Dao
internal interface IgnoredDao {

    @Query("SELECT * FROM IgnoredGameId")
    fun observeAll(): Flow<List<IgnoredGameIdEntry>>

    /** Whether [gameId] is on the ignore list (drives the toggle's add-vs-remove choice). */
    @Query("SELECT EXISTS(SELECT 1 FROM IgnoredGameId WHERE gameId IS :gameId)")
    suspend fun contains(gameId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(vararg entries: IgnoredGameIdEntry)

    @Query("DELETE FROM IgnoredGameId WHERE gameId IS :gameId")
    suspend fun delete(gameId: String)

    @Query("DELETE FROM IgnoredGameId")
    suspend fun clear()

    /** Atomically swaps the id set so observers never see an empty intermediate state (remote-as-truth refresh). */
    @Transaction
    suspend fun replaceAll(gameIds: List<String>) {
        clear()
        add(*gameIds.map { IgnoredGameIdEntry(it) }.toTypedArray())
    }
}
