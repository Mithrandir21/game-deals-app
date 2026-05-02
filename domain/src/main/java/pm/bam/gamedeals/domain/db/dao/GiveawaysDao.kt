package pm.bam.gamedeals.domain.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import pm.bam.gamedeals.domain.db.entities.GiveawayEntity
import pm.bam.gamedeals.domain.models.Giveaway

@Dao
internal interface GiveawaysDao {

    /** Returns all the [Giveaway]s in the database. */
    @Query("SELECT * FROM Giveaway")
    fun observeAllGiveaways(): Flow<List<Giveaway>>

    /** Adds the [GiveawayEntity] to the database. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addGiveawayEntities(vararg entities: GiveawayEntity)

}
