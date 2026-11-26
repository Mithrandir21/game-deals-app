package pm.bam.gamedeals.domain.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import pm.bam.gamedeals.domain.models.Store

@Dao
internal interface StoresDao {

    /** Returns all the [Store]s in the database. */
    @Query("SELECT * FROM Store")
    fun observeAllStores(): Flow<List<Store>>

    /** Returns all the [Store]s in the database. */
    @Query("SELECT * FROM Store")
    suspend fun getAllStores(): List<Store>

    /**
     * Returns the [Store] in the database where the [Store.storeID] is [storeId], or `null` when the
     * shop isn't cached. Nullable deliberately: deals can reference a shop the cached store list
     * doesn't cover yet (a shop ITAD added since the last refresh), and Room throws on a non-null
     * return type when the query comes back empty.
     */
    @Query("SELECT * FROM Store WHERE storeID = :storeId")
    suspend fun getStore(storeId: Int): Store?

    /** Adds the [Store] to the database. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addStores(vararg genericItem: Store)

}