package pm.bam.gamedeals.domain.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import pm.bam.gamedeals.domain.models.Deal


@Dao
internal interface DealsDao {

    /** Returns all the [Deal]s in the database, ordered by their [Deal.releaseDate] in descending order. */
    @Query("SELECT * FROM Deal ORDER BY releaseDate DESC")
    fun observeAllDeals(): Flow<List<Deal>>

    /** Returns all the [Deal]s in the database with [storeId], ordered by their [Deal.dealRating] in descending order. */
    @Query("SELECT * FROM Deal WHERE storeID IS :storeId ORDER BY dealRating DESC")
    suspend fun getStoreDeals(storeId: Int): List<Deal>

    /** Returns all the [Deal]s in the database with [storeId], ordered by their [Deal.dealRating] in descending order, limited to [limit]. */
    @Query("SELECT * FROM Deal WHERE storeID IS :storeId ORDER BY dealRating DESC LIMIT :limit")
    suspend fun getStoreDeals(storeId: Int, limit: Int): List<Deal>

    /** Returns all the [Deal]s in the database with [storeId], ordered by their [Deal.dealRating] in descending order. */
    @Query("SELECT * FROM Deal WHERE storeID IS :storeId ORDER BY dealRating DESC")
    fun getPagingStoreDeals(storeId: Int): PagingSource<Int, Deal>

    /** Adds the [Deal] to the database. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addDeals(vararg genericItem: Deal)

    /** Deletes all [Deal]s from the database where the [Deal.storeID] is [storeId]. */
    @Query("DELETE FROM Deal WHERE storeID IS :storeId")
    suspend fun clearDealsForStore(storeId: Int)
}