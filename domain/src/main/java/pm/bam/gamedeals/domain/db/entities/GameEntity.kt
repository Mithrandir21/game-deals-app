package pm.bam.gamedeals.domain.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "Game")
internal data class GameEntity(
    @PrimaryKey
    val gameID: Int,
    val steamAppID: Int? = null,
    val cheapestValue: Double,
    val cheapestDenominated: String,
    val cheapestDealID: String,
    val title: String,
    val internalName: String,
    val thumb: String,
)
