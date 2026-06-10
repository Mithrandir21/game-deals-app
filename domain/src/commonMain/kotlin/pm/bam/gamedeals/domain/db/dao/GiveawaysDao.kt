package pm.bam.gamedeals.domain.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import pm.bam.gamedeals.domain.models.Giveaway

@Dao
internal interface GiveawaysDao {

    @Query("SELECT * FROM Giveaway")
    fun observeAllGiveaways(): Flow<List<Giveaway>>

    /** One-shot snapshot of all [Giveaway]s, used by the cache to evaluate TTL freshness. */
    @Query("SELECT * FROM Giveaway")
    suspend fun getAllGiveaways(): List<Giveaway>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addGiveaways(vararg genericItem: Giveaway)

    @Query("DELETE FROM Giveaway")
    suspend fun clearGiveaways()

    /** Atomically swaps the table contents so observers never see an empty intermediate state. */
    @Transaction
    suspend fun replaceAll(giveaways: List<Giveaway>) {
        clearGiveaways()
        addGiveaways(*giveaways.toTypedArray())
    }

}
