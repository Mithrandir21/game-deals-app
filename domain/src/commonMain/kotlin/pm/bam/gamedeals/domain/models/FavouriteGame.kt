package pm.bam.gamedeals.domain.models

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "FavouriteGame")
@Serializable
data class FavouriteGame(
    @PrimaryKey
    val gameID: Int,
    val title: String,
    val thumb: String,
    val dateAddedMs: Long,
)
