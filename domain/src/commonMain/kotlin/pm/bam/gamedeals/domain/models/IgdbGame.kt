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
            Bluesky, Xbox, Other,
        }
    }

    @Immutable
    data class IgdbSimilarGame(
        val id: Long,
        val name: String,
        val coverImageId: String?,
    )
}
