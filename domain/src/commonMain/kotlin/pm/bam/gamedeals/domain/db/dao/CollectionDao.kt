package pm.bam.gamedeals.domain.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import pm.bam.gamedeals.domain.db.cache.CollectionGameIdEntry

@Dao
internal interface CollectionDao {

    @Query("SELECT * FROM CollectionGameId")
    fun observeAll(): Flow<List<CollectionGameIdEntry>>

    /** Whether [gameId] is in the collection (drives the toggle's add-vs-remove choice). */
    @Query("SELECT EXISTS(SELECT 1 FROM CollectionGameId WHERE gameId IS :gameId)")
    suspend fun contains(gameId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(vararg entries: CollectionGameIdEntry)

    @Query("DELETE FROM CollectionGameId WHERE gameId IS :gameId")
    suspend fun delete(gameId: String)

    @Query("DELETE FROM CollectionGameId")
    suspend fun clear()

    /** Atomically swaps the id set so observers never see an empty intermediate state (remote-as-truth refresh). */
    @Transaction
    suspend fun replaceAll(gameIds: List<String>) {
        clear()
        add(*gameIds.map { CollectionGameIdEntry(it) }.toTypedArray())
    }
}
