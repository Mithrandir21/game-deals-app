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
    /**
     * HowLongToBeat-style completion estimates (epic #291, Phase 2). Fetched separately from IGDB's
     * `/v4/game_time_to_beats` endpoint (not part of the games dot-expansion) and merged in by the
     * consumer, so it is null on a bare game lookup until enriched.
     */
    val timeToBeat: IgdbTimeToBeat? = null,
    val steamAppId: Int? = null,
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

    /** HowLongToBeat completion estimates in **seconds** (IGDB `game_time_to_beats`); UI formats to hours. */
    @Immutable
    data class IgdbTimeToBeat(
        val hastily: Long? = null,
        val normally: Long? = null,
        val completely: Long? = null,
        val count: Long? = null,
    )
}
