package pm.bam.gamedeals.domain.models

import androidx.compose.runtime.Immutable
import kotlin.time.Instant
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class IgdbGame(
    val id: Long,
    val name: String,
    val summary: String?,
    val storyline: String? = null,
    val coverImageId: String? = null,
    val screenshotImageIds: ImmutableList<String> = persistentListOf(),
    val firstReleaseDate: Instant? = null,
    val rating: Double? = null,
    val ratingCount: Long? = null,
    val aggregatedRating: Double? = null,
    val aggregatedRatingCount: Long? = null,
    val genres: ImmutableList<String> = persistentListOf(),
    val themes: ImmutableList<String> = persistentListOf(),
    val involvedCompanies: ImmutableList<IgdbCompanyRole> = persistentListOf(),
    val websites: ImmutableList<IgdbWebsite> = persistentListOf(),
    val similarGames: ImmutableList<IgdbSimilarGame> = persistentListOf(),
    val dlcs: ImmutableList<IgdbSimilarGame> = persistentListOf(),
    val expansions: ImmutableList<IgdbSimilarGame> = persistentListOf(),
    /** Platform labels (IGDB `platforms`, abbreviation preferred over full name) — "PC", "PS5", "Switch". */
    val platforms: ImmutableList<String> = persistentListOf(),
    /** Trailers/gameplay clips (IGDB `videos`) — each carries a YouTube id the UI opens externally. */
    val videos: ImmutableList<IgdbVideo> = persistentListOf(),
    /** Series/franchises (IGDB `franchises`) this game belongs to, with their other member games (#7). */
    val franchises: ImmutableList<IgdbFranchise> = persistentListOf(),
    /**
     * HowLongToBeat-style completion estimates (epic #291, Phase 2). Fetched separately from IGDB's
     * `/v4/game_time_to_beats` endpoint (not part of the games dot-expansion) and merged in by the
     * consumer, so it is null on a bare game lookup until enriched.
     */
    val timeToBeat: IgdbTimeToBeat? = null,
    val steamAppId: Int? = null,
    /**
     * IGDB `total_rating_count` — combined count of user + critic ratings. Used purely as a
     * popularity proxy to sort tag-discovery results (epic #307); null on lookups that don't
     * request it.
     */
    val totalRatingCount: Long? = null,
) {

    @Immutable
    data class IgdbCompanyRole(
        val companyName: String,
        val role: Role,
    ) {
        enum class Role { Developer, Publisher, Porting, Supporting }
    }

    @Immutable
    data class IgdbWebsite(
        val url: String,
        val category: Category,
    ) {
        /** IGDB website_type IDs — see /v4/website_types. Stable across the category → type migration. */
        enum class Category {
            Official, Wikia, Wikipedia, Facebook, Twitter, Twitch, Instagram, YouTube,
            IPhone, IPad, Android, Steam, Reddit, Itch, EpicStore, GogStore, Discord,
            Bluesky, Xbox, PlayStation, Nintendo, Other,
        }
    }

    @Immutable
    data class IgdbSimilarGame(
        val id: Long,
        val name: String,
        val coverImageId: String?,
    )

    /** A trailer/gameplay clip. [videoId] is a YouTube id (e.g. `dQw4w9WgXcQ`); [name] is its label. */
    @Immutable
    data class IgdbVideo(
        val videoId: String,
        val name: String?,
    )

    /** A franchise/series and its other member games (the current game is excluded). */
    @Immutable
    data class IgdbFranchise(
        val id: Long,
        val name: String,
        val games: ImmutableList<IgdbSimilarGame> = persistentListOf(),
    )

    /** HowLongToBeat completion estimates in **seconds** (IGDB `game_time_to_beats`); UI formats to hours. */
    @Immutable
    data class IgdbTimeToBeat(
        val hastily: Long? = null,
        val normally: Long? = null,
        val completely: Long? = null,
        val count: Long? = null,
    )
}
