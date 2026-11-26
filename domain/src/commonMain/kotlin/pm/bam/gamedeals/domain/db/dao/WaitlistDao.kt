package pm.bam.gamedeals.domain.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import pm.bam.gamedeals.domain.db.cache.WaitlistGameIdEntry

@Dao
internal interface WaitlistDao {

    @Query("SELECT * FROM WaitlistGameId")
    fun observeAll(): Flow<List<WaitlistGameIdEntry>>

    /** Whether [gameId] is on the waitlist (drives the optimistic-free toggle's add-vs-remove choice). */
    @Query("SELECT EXISTS(SELECT 1 FROM WaitlistGameId WHERE gameId IS :gameId)")
    suspend fun contains(gameId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(vararg entries: WaitlistGameIdEntry)

    @Query("DELETE FROM WaitlistGameId WHERE gameId IS :gameId")
    suspend fun delete(gameId: String)

    @Query("DELETE FROM WaitlistGameId")
    suspend fun clear()

    /** Atomically swaps the id set so observers never see an empty intermediate state (remote-as-truth refresh). */
    @Transaction
    suspend fun replaceAll(gameIds: List<String>) {
        clear()
        add(*gameIds.map { WaitlistGameIdEntry(it) }.toTypedArray())
    }
}
