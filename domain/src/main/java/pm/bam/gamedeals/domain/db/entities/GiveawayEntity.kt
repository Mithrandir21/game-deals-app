package pm.bam.gamedeals.domain.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import pm.bam.gamedeals.domain.models.GiveawayPlatform
import pm.bam.gamedeals.domain.models.GiveawayType
import java.time.LocalDateTime

@Entity(tableName = "Giveaway")
internal data class GiveawayEntity(
    @PrimaryKey
    val id: Int,
    val title: String,
    val worthDenominated: String?,
    val worth: Double?,
    val thumbnail: String,
    val image: String,
    val description: String,
    val instructions: String,
    val openGiveawayUrl: String,
    val publishedDate: LocalDateTime,
    val type: GiveawayType,
    val platforms: List<GiveawayPlatform>,
    val endDate: String?,
    val users: Int,
    val status: String,
    val gamerpowerUrl: String,
    val openGiveaway: String,
)
