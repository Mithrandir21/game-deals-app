package pm.bam.gamedeals.remote.gamerpower.models


import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * A GamerPower giveaway row. GamerPower is a community-run feed, so this is treated as a *display* DTO:
 * only [id] (the dedup key) and [title] are required; every other field defaults so a row missing a
 * non-essential value still renders instead of failing the whole batch. Rows that can't satisfy even
 * [id]/[title] are dropped by the list-level skip-invalid decode (see GamesApi).
 */
@Serializable
data class RemoteGiveaway(
    @SerialName("id")
    val id: Int,
    @SerialName("title")
    val title: String,
    @SerialName("worth")
    val worth: String = "",
    @SerialName("thumbnail")
    val thumbnail: String = "",
    @SerialName("image")
    val image: String = "",
    @SerialName("description")
    val description: String = "",
    @SerialName("instructions")
    val instructions: String = "",
    @SerialName("open_giveaway_url")
    val openGiveawayUrl: String = "",
    @SerialName("published_date")
    val publishedDate: String = "",
    @SerialName("type")
    val type: RemoteGiveawayType = RemoteGiveawayType.UNKNOWN,
    @SerialName("platforms")
    val platforms: String = "",
    @SerialName("end_date")
    val endDate: String = "",
    @SerialName("users")
    val users: Int = 0,
    @SerialName("status")
    val status: String = "",
    @SerialName("gamerpower_url")
    val gamerpowerUrl: String = "",
    @SerialName("open_giveaway")
    val openGiveaway: String = "",
)

/**
 * Giveaway category. A custom serializer maps any unrecognized wire value to [UNKNOWN] instead of
 * throwing — without it, a single new GamerPower type (e.g. "Bundle", "Key") would fail the *entire*
 * giveaways response. [UNKNOWN] is bucketed in the domain mapper.
 */
@Serializable(with = RemoteGiveawayTypeSerializer::class)
enum class RemoteGiveawayType {
    GAME,
    DLC,
    BETA,
    OTHER,
    UNKNOWN,
}

internal object RemoteGiveawayTypeSerializer : KSerializer<RemoteGiveawayType> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("RemoteGiveawayType", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): RemoteGiveawayType =
        when (decoder.decodeString()) {
            "Game" -> RemoteGiveawayType.GAME
            "DLC" -> RemoteGiveawayType.DLC
            "Early Access" -> RemoteGiveawayType.BETA
            "Other" -> RemoteGiveawayType.OTHER
            else -> RemoteGiveawayType.UNKNOWN
        }

    override fun serialize(encoder: Encoder, value: RemoteGiveawayType) {
        encoder.encodeString(
            when (value) {
                RemoteGiveawayType.GAME -> "Game"
                RemoteGiveawayType.DLC -> "DLC"
                RemoteGiveawayType.BETA -> "Early Access"
                RemoteGiveawayType.OTHER, RemoteGiveawayType.UNKNOWN -> "Other"
            },
        )
    }
}
