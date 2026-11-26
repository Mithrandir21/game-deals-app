package pm.bam.gamedeals.remote.igdb.models

import kotlinx.serialization.Serializable

/**
 * A row from any of IGDB's small vocabulary endpoints — `/v4/genres`, `/v4/themes`,
 * `/v4/game_modes`, `/v4/player_perspectives`, `/v4/keywords` — which all share the
 * `{ id, name, slug }` shape (epic #307). The endpoint determines the tag dimension, so it isn't
 * carried on the wire.
 */
@Serializable
data class RemoteIgdbTag(
    val id: Long,
    val name: String? = null,
    val slug: String? = null,
)
