package pm.bam.gamedeals.domain.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import pm.bam.gamedeals.domain.models.Release

@Dao
internal interface ReleasesDao {

    @Query("SELECT * FROM `Release`")
    fun observeAllReleases(): Flow<List<Release>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addReleases(vararg genericItem: Release)

    @Query("DELETE FROM `Release`")
    suspend fun clearReleases()

    /** Atomically swaps the table contents so observers never see an empty intermediate state. */
    @Transaction
    suspend fun replaceAll(releases: List<Release>) {
        clearReleases()
        addReleases(*releases.toTypedArray())
    }

}
