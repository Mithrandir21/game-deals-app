package pm.bam.gamedeals.remote.igdb.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class RemoteIgdbGame(
    val id: Long,
    val name: String,
    val summary: String? = null,
    val storyline: String? = null,
    val cover: RemoteIgdbCover? = null,
    val screenshots: List<RemoteIgdbScreenshot> = emptyList(),
    @SerialName("first_release_date") val firstReleaseDate: Long? = null,
    val rating: Double? = null,
    @SerialName("rating_count") val ratingCount: Long? = null,
    @SerialName("aggregated_rating") val aggregatedRating: Double? = null,
    @SerialName("aggregated_rating_count") val aggregatedRatingCount: Long? = null,
    @SerialName("total_rating_count") val totalRatingCount: Long? = null,
    val genres: List<RemoteIgdbGenre> = emptyList(),
    val themes: List<RemoteIgdbTheme> = emptyList(),
    @SerialName("involved_companies") val involvedCompanies: List<RemoteIgdbInvolvedCompany> = emptyList(),
    val websites: List<RemoteIgdbWebsite> = emptyList(),
    @SerialName("similar_games") val similarGames: List<RemoteIgdbSimilarGame> = emptyList(),
    // DLCs and expansions share the similar-game shape (id, name, cover) so they map through the same path.
    val dlcs: List<RemoteIgdbSimilarGame> = emptyList(),
    val expansions: List<RemoteIgdbSimilarGame> = emptyList(),
    @SerialName("external_games") val externalGames: List<RemoteIgdbExternalGame> = emptyList(),
)

/**
 * Row from IGDB's `/v4/game_time_to_beats` endpoint (epic #291, Phase 2). Completion times are in
 * **seconds**; any tier IGDB lacks data for is null. Keyed by `game_id` on the request, not returned here.
 */
@Serializable
data class RemoteIgdbTimeToBeat(
    val hastily: Long? = null,
    val normally: Long? = null,
    val completely: Long? = null,
    val count: Long? = null,
)

@Serializable
data class RemoteIgdbExternalGame(
    val uid: String? = null,
    @SerialName("external_game_source") val externalGameSource: Long? = null,
)

@Serializable
data class RemoteIgdbCover(
    val id: Long,
    @SerialName("image_id") val imageId: String? = null,
)

@Serializable
data class RemoteIgdbScreenshot(
    val id: Long,
    @SerialName("image_id") val imageId: String? = null,
)

@Serializable
data class RemoteIgdbGenre(
    val id: Long,
    val name: String? = null,
)

@Serializable
data class RemoteIgdbTheme(
    val id: Long,
    val name: String? = null,
)

@Serializable
data class RemoteIgdbInvolvedCompany(
    val id: Long,
    val company: RemoteIgdbCompany? = null,
    val developer: Boolean = false,
    val publisher: Boolean = false,
    val porting: Boolean = false,
    val supporting: Boolean = false,
)

@Serializable
data class RemoteIgdbCompany(
    val id: Long,
    val name: String? = null,
)

@Serializable
data class RemoteIgdbWebsite(
    val id: Long,
    val url: String? = null,
    val type: RemoteIgdbWebsiteType? = null,
)

@Serializable
data class RemoteIgdbWebsiteType(
    val id: Long,
    val type: String? = null,
)

@Serializable
data class RemoteIgdbSimilarGame(
    val id: Long,
    val name: String? = null,
    val cover: RemoteIgdbCover? = null,
)
