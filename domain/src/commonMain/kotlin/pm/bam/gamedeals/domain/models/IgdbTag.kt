package pm.bam.gamedeals.domain.models

import androidx.compose.runtime.Immutable

/**
 * A single selectable tag in the "discover by tag" picker (epic #307). IGDB splits its tag-like
 * metadata across several endpoints; we surface a curated subset of each as one flat vocabulary.
 * Ids are only unique *within* a dimension, so [dimension] + [igdbId] together identify a tag.
 */
@Immutable
data class IgdbTag(
    val dimension: IgdbTagDimension,
    val igdbId: Long,
    val name: String,
    val slug: String? = null,
)

/**
 * The IGDB metadata dimensions we expose in the tag picker. Genres/themes/game-modes/perspectives
 * are small curated enums (`/v4/genres` etc.); keywords are a huge community list, so only a
 * hand-curated allow-list of keyword slugs is included (see `CuratedKeywords`).
 */
/** IGDB's "Erotic" theme, hidden unless the user opted into mature content. */
const val IGDB_EROTIC_THEME_ID = 42L

val IgdbTag.isMature: Boolean
    get() = dimension == IgdbTagDimension.Theme && igdbId == IGDB_EROTIC_THEME_ID

enum class IgdbTagDimension {
    Genre,
    Theme,
    GameMode,
    PlayerPerspective,
    Keyword,
}
