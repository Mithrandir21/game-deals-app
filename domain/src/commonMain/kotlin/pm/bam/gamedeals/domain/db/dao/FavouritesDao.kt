package pm.bam.gamedeals.domain.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import pm.bam.gamedeals.domain.models.FavouriteGame

@Dao
internal interface FavouritesDao {

    /** Returns all favourites ordered by most-recently added first. */
    @Query("SELECT * FROM FavouriteGame ORDER BY dateAddedMs DESC")
    fun observeAllFavourites(): Flow<List<FavouriteGame>>

    /** Returns just the gameIDs — used to drive list-row indicator overlays. */
    @Query("SELECT gameID FROM FavouriteGame")
    fun observeFavouriteIds(): Flow<List<String>>

    /** Reactive boolean for a single game — drives the GameScreen heart icon. */
    @Query("SELECT EXISTS(SELECT 1 FROM FavouriteGame WHERE gameID = :gameId)")
    fun observeIsFavourite(gameId: String): Flow<Boolean>

    /** Synchronous one-shot read used inside [toggleFavourite] for atomic read-modify-write. */
    @Query("SELECT EXISTS(SELECT 1 FROM FavouriteGame WHERE gameID = :gameId)")
    suspend fun isFavouriteNow(gameId: String): Boolean

    /** REPLACE so re-favouriting refreshes [FavouriteGame.dateAddedMs]. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavourites(vararg favourites: FavouriteGame)

    @Query("DELETE FROM FavouriteGame WHERE gameID = :gameId")
    suspend fun removeFavouriteById(gameId: String)

    /**
     * Atomic toggle: reads current favourite state and inserts/deletes within a single
     * transaction so concurrent callers can't both observe "not favourite" and double-insert
     * (or both observe "favourite" and have one of the deletes lose). Returns the new state.
     */
    @Transaction
    suspend fun toggleFavourite(gameId: String, title: String, thumb: String, dateAddedMs: Long): Boolean {
        val nowFavourite = !isFavouriteNow(gameId)
        if (nowFavourite) {
            addFavourites(
                FavouriteGame(
                    gameID = gameId,
                    title = title,
                    thumb = thumb,
                    dateAddedMs = dateAddedMs,
                )
            )
        } else {
            removeFavouriteById(gameId)
        }
        return nowFavourite
    }
}
