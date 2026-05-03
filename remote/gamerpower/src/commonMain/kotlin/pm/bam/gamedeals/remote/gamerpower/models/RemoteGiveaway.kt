package pm.bam.gamedeals.remote.gamerpower.models


import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RemoteGiveaway(
    @SerialName("id")
    val id: Int,
    @SerialName("title")
    val title: String,
    @SerialName("worth")
    val worth: String,
    @SerialName("thumbnail")
    val thumbnail: String,
    @SerialName("image")
    val image: String,
    @SerialName("description")
    val description: String,
    @SerialName("instructions")
    val instructions: String,
    @SerialName("open_giveaway_url")
    val openGiveawayUrl: String,
    @SerialName("published_date")
    val publishedDate: String,
    @SerialName("type")
    val type: RemoteGiveawayType,
    @SerialName("platforms")
    val platforms: String,
    @SerialName("end_date")
    val endDate: String,
    @SerialName("users")
    val users: Int,
    @SerialName("status")
    val status: String,
    @SerialName("gamerpower_url")
    val gamerpowerUrl: String,
    @SerialName("open_giveaway")
    val openGiveaway: String,
)

enum class RemoteGiveawayType {
    @SerialName("Game")
    GAME,

    @SerialName("DLC")
    DLC,

    @SerialName("Early Access")
    BETA,

    @SerialName("Other")
    OTHER,
}