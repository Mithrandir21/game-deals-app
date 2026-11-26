package pm.bam.gamedeals.common.ui.components

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.RecentlyViewedGame

/**
 * Device UI coverage for [RecentlyViewedCarousel]. The carousel takes hoisted state + lambda callbacks,
 * so it's driven directly with fixture [RecentlyViewedGame]s (no ViewModel/Koin). Each tile is matched by
 * its accessibility content description — the title `Text` clears its own semantics, so it isn't matchable
 * by text.
 *
 * The height assertion guards a layout regression: the tile title is capped at two lines, and without a
 * matching floor a short title produced a shorter tile. Since the `LazyRow` is unconstrained, its height is
 * the max of the composed tiles, so that max changed as tiles scrolled in and out and the whole Home feed
 * below the carousel shifted.
 */
class RecentlyViewedCarouselTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent(games: List<RecentlyViewedGame>) {
        composeTestRule.setContent {
            GameDealsTheme {
                RecentlyViewedCarousel(
                    games = persistentListOf(*games.toTypedArray()),
                    onOpen = {},
                    onRemove = {},
                    onClearAll = {},
                )
            }
        }
    }

    @Test
    fun tilesKeepAConstantHeightAcrossTitleLengths() {
        setContent(MIXED_TITLE_GAMES)

        val heights = MIXED_TITLE_GAMES.map { game ->
            composeTestRule.onNodeWithContentDescription(game.title, substring = true)
                .fetchSemanticsNode().size.height
        }

        assertEquals(
            "Tile heights differ ($heights), so the LazyRow resizes as tiles scroll in and out.",
            1,
            heights.toSet().size,
        )
    }

    @Test
    fun rendersNothingWhenHistoryIsEmpty() {
        setContent(emptyList())

        composeTestRule.onNodeWithContentDescription(SHORT_TITLE, substring = true).assertDoesNotExist()
    }

    private companion object {
        const val SHORT_TITLE = "Hades"

        /**
         * A one-line title, a title long enough to wrap to two lines, then another one-line title. Only
         * three 110dp tiles fit on a phone and a `LazyRow` doesn't compose off-screen items, so the
         * fixture stops at what's actually measurable.
         */
        val MIXED_TITLE_GAMES = listOf(
            RecentlyViewedGame(gameId = "1", title = SHORT_TITLE, boxart = null),
            RecentlyViewedGame(gameId = "2", title = "The Elder Scrolls V: Skyrim Special Edition", boxart = null),
            RecentlyViewedGame(gameId = "3", title = "Doom", boxart = null),
        )
    }
}
