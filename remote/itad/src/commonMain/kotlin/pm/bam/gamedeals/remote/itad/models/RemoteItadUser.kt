package pm.bam.gamedeals.remote.itad.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `/user/info/v2` response (epic #219, Phase 2) — currently just the username. */
@Serializable
data class RemoteItadUser(
    @SerialName("username") val username: String,
)
