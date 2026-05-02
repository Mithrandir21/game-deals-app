package pm.bam.gamedeals.domain.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Release")
internal data class ReleaseEntity(
    @PrimaryKey
    val title: String,
    val date: Int,
    val image: String,
)
