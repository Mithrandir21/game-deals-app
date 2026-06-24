package pm.bam.gamedeals.remote.itad.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `/user/notes/v1` item (epic #272, P4.1 #282) — a game note: `gid` is the game id, `note` the text. */
@Serializable
data class RemoteItadNote(
    @SerialName("gid") val gid: String,
    @SerialName("note") val note: String,
)
