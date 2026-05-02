package pm.bam.gamedeals.domain.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import pm.bam.gamedeals.domain.db.entities.DealPageEntity

@Dao
internal interface PagingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(dealPage: DealPageEntity)

    @Query("SELECT * FROM DealPage WHERE storeID = :storeId")
    suspend fun getStorePage(storeId: Int): DealPageEntity?

    @Query("DELETE FROM DealPage WHERE storeID IS :storeId")
    suspend fun clearStorePage(storeId: Int)
}
