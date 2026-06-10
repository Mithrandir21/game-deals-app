package pm.bam.gamedeals.domain.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import pm.bam.gamedeals.domain.models.Deal


@Dao
internal interface DealsDao {

    /** Returns all the [Deal]s in the database, ordered by their [Deal.releaseDate] in descending order. */
    @Query("SELECT * FROM Deal ORDER BY savings DESC")
    fun observeAllDeals(): Flow<List<Deal>>

    /** Returns the cached [Deal]s for [storeId] in [country], ordered by savings descending. */
    @Query("SELECT * FROM Deal WHERE storeID IS :storeId AND country IS :country ORDER BY savings DESC")
    suspend fun getStoreDeals(storeId: Int, country: String): List<Deal>

    /** Returns the cached [Deal]s for [storeId] in [country], ordered by savings descending, limited to [limit]. */
    @Query("SELECT * FROM Deal WHERE storeID IS :storeId AND country IS :country ORDER BY savings DESC LIMIT :limit")
    suspend fun getStoreDeals(storeId: Int, country: String, limit: Int): List<Deal>

    /** Live stream of the cached [Deal]s for [storeId] in [country], ordered by savings descending. */
    @Query("SELECT * FROM Deal WHERE storeID IS :storeId AND country IS :country ORDER BY savings DESC")
    fun observeStoreDeals(storeId: Int, country: String): Flow<List<Deal>>

    /** Adds the [Deal] to the database. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addDeals(vararg genericItem: Deal)

    /** Deletes the cached [Deal]s for [storeId] in [country] (the active region's store rows). */
    @Query("DELETE FROM Deal WHERE storeID IS :storeId AND country IS :country")
    suspend fun clearDealsForStore(storeId: Int, country: String)

    /** Deletes every cached [Deal] — used to invalidate the cache when the region changes (#212). */
    @Query("DELETE FROM Deal")
    suspend fun clearAllDeals()
}