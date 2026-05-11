package pm.bam.gamedeals.domain.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import pm.bam.gamedeals.domain.models.FavouriteGame

@Dao
internal interface FavouritesDao {

    /** Returns all favourites ordered by most-recently added first. */
    @Query("SELECT * FROM FavouriteGame ORDER BY dateAddedMs DESC")
    fun observeAllFavourites(): Flow<List<FavouriteGame>>

    /** Returns just the gameIDs — used to drive list-row indicator overlays. */
    @Query("SELECT gameID FROM FavouriteGame")
    fun observeFavouriteIds(): Flow<List<Int>>

    /** Reactive boolean for a single game — drives the GameScreen heart icon. */
    @Query("SELECT EXISTS(SELECT 1 FROM FavouriteGame WHERE gameID = :gameId)")
    fun observeIsFavourite(gameId: Int): Flow<Boolean>

    /** REPLACE so re-favouriting refreshes [FavouriteGame.dateAddedMs]. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavourites(vararg favourites: FavouriteGame)

    @Query("DELETE FROM FavouriteGame WHERE gameID = :gameId")
    suspend fun removeFavouriteById(gameId: Int)
}
