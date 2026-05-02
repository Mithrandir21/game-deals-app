package pm.bam.gamedeals.domain.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import pm.bam.gamedeals.domain.db.entities.StoreEntity
import pm.bam.gamedeals.domain.models.Store

@Dao
internal interface StoresDao {

    /** Returns all the [Store]s in the database. */
    @Query("SELECT $STORE_PUBLIC_COLUMNS FROM Store")
    fun observeAllStores(): Flow<List<Store>>

    /** Returns all the [Store]s in the database. */
    @Query("SELECT $STORE_PUBLIC_COLUMNS FROM Store")
    suspend fun getAllStores(): List<Store>

    /** Returns the [Store] in the database where the [Store.storeID] is [storeId]. */
    @Query("SELECT $STORE_PUBLIC_COLUMNS FROM Store WHERE storeID = :storeId")
    suspend fun getStore(storeId: Int): Store

    /** Internal cache-side read returning full entities (including `expires`). */
    @Query("SELECT * FROM Store")
    suspend fun getAllStoreEntities(): List<StoreEntity>

    /** Adds the [StoreEntity] to the database. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addStoreEntities(vararg entities: StoreEntity)
}

private const val STORE_PUBLIC_COLUMNS = "storeID, storeName, isActive, images"
