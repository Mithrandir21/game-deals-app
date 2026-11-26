package pm.bam.gamedeals.remote.igdb.models

import kotlinx.serialization.Serializable

/**
 * Row from IGDB's `/v4/external_games` lookup with Apicalypse dot-notation expansion
 * (`fields game.id,game.name,game.summary;`). `game` is nullable because IGDB occasionally
 * returns an external_game row whose `game` reference hasn't been populated.
 */
@Serializable
data class RemoteExternalGameLookup(
    val id: Long,
    val game: RemoteIgdbGame? = null,
)
