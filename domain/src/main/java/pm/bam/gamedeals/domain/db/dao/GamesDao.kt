package pm.bam.gamedeals.domain.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import pm.bam.gamedeals.domain.db.entities.GameEntity
import pm.bam.gamedeals.domain.models.Game

@Dao
internal interface GamesDao {

    /** Returns all the [Game]s in the database. */
    @Query("SELECT * FROM Game")
    fun observeAllGames(): Flow<List<Game>>

    /** Adds the [GameEntity] to the database. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addGameEntities(vararg entities: GameEntity)
}
