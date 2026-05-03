package pm.bam.gamedeals.domain.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import pm.bam.gamedeals.domain.models.Release

@Dao
internal interface ReleasesDao {

    /** Returns all the [Release]s in the database. */
    @Query("SELECT * FROM `Release`")
    fun observeAllReleases(): Flow<List<Release>>

    /** Adds the [Release] to the database. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addReleases(vararg genericItem: Release)

}