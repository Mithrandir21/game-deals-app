package pm.bam.gamedeals.remote.cheapshark.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RemoteRelease(
    @SerialName("date")
    val date: Int,
    @SerialName("title")
    val title: String,
    @SerialName("image")
    val image: String
)