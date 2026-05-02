package pm.bam.gamedeals.domain.db.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import pm.bam.gamedeals.domain.db.entities.DealEntity
import pm.bam.gamedeals.domain.models.Deal


@Dao
internal interface DealsDao {

    /** Returns all the [Deal]s in the database, ordered by their [Deal.releaseDate] in descending order. */
    @Query("SELECT $DEAL_PUBLIC_COLUMNS FROM Deal ORDER BY releaseDate DESC")
    fun observeAllDeals(): Flow<List<Deal>>

    /** Returns all the [Deal]s in the database with [storeId], ordered by `dealRating` in descending order. */
    @Query("SELECT $DEAL_PUBLIC_COLUMNS FROM Deal WHERE storeID IS :storeId ORDER BY dealRating DESC")
    suspend fun getStoreDeals(storeId: Int): List<Deal>

    /** Returns all the [Deal]s in the database with [storeId], ordered by `dealRating` in descending order, limited to [limit]. */
    @Query("SELECT $DEAL_PUBLIC_COLUMNS FROM Deal WHERE storeID IS :storeId ORDER BY dealRating DESC LIMIT :limit")
    suspend fun getStoreDeals(storeId: Int, limit: Int): List<Deal>

    /** Paged [Deal]s for [storeId], ordered by `dealRating` in descending order. */
    @Query("SELECT $DEAL_PUBLIC_COLUMNS FROM Deal WHERE storeID IS :storeId ORDER BY dealRating DESC")
    fun getPagingStoreDeals(storeId: Int): PagingSource<Int, Deal>

    /** Internal cache-side read returning full entities (including `expires`). */
    @Query("SELECT * FROM Deal WHERE storeID IS :storeId")
    suspend fun getStoreDealEntities(storeId: Int): List<DealEntity>

    /** Adds the [DealEntity] to the database. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addDealEntities(vararg entities: DealEntity)

    /** Deletes all rows from the `Deal` table where `storeID` is [storeId]. */
    @Query("DELETE FROM Deal WHERE storeID IS :storeId")
    suspend fun clearDealsForStore(storeId: Int)
}

private const val DEAL_PUBLIC_COLUMNS =
    "dealID, internalName, title, metacriticLink, storeID, gameID, " +
        "salePriceValue, salePriceDenominated, normalPriceValue, normalPriceDenominated, " +
        "isOnSale, savings, metacriticScore, steamRatingText, steamRatingPercent, " +
        "steamRatingCount, steamAppID, releaseDate, lastChange, dealRating, thumb"
