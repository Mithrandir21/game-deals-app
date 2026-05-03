package pm.bam.gamedeals.domain.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import pm.bam.gamedeals.domain.models.DealPage

@Dao
internal interface PagingDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(dealPage: DealPage)

    @Query("SELECT * FROM DealPage WHERE storeID = :storeId")
    suspend fun getStorePage(storeId: Int): DealPage?

    @Query("DELETE FROM DealPage WHERE storeID IS :storeId")
    suspend fun clearStorePage(storeId: Int)
}