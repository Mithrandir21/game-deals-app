package pm.bam.gamedeals.remote.igdb.models

import kotlinx.serialization.Serializable

@Serializable
data class RemoteIgdbGame(val id: Long, val name: String)
