package pm.bam.gamedeals.feature.game.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.IgdbGame

/**
 * Device UI coverage for [IgdbGameTile], the cover tile used by the "Similar games" and "Series" rows on
 * the game page. The tile takes hoisted state + a click lambda, so it's driven directly with fixture data
 * inside a `LazyRow` that mirrors the real call sites in [GamePageAboutTab].
 *
 * Guards a layout regression: the tile name is capped at two lines, and without a matching floor a short
 * name produced a shorter tile. The row is unconstrained, so its height is the max of the composed tiles —
 * if that varies, the row resizes as tiles scroll in and out and the content below it shifts.
 */
class IgdbGameTileTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setContent() {
        composeTestRule.setContent {
            GameDealsTheme {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(MIXED_NAME_GAMES) { game ->
                        IgdbGameTile(game = game, onClick = {}, modifier = Modifier.width(TILE_WIDTH))
                    }
                }
            }
        }
    }

    @Test
    fun tilesKeepAConstantHeightAcrossNameLengths() {
        setContent()

        // The tile Column is clickable, which merges its descendants — so matching the name text
        // resolves to the tile node itself, not the inner Text.
        val heights = MIXED_NAME_GAMES.map { game ->
            composeTestRule.onNodeWithText(game.name).fetchSemanticsNode().size.height
        }

        assertEquals(
            "Tile heights differ ($heights), so the LazyRow resizes as tiles scroll in and out.",
            1,
            heights.toSet().size,
        )
    }

    private companion object {
        /** Matches the width the real "Similar games" / "Series" rows apply in [GamePageAboutTab]. */
        val TILE_WIDTH = 112.dp

        /**
         * A one-line name, a name long enough to wrap to two lines, then another one-line name. Only three
         * tiles fit on a phone and a `LazyRow` doesn't compose off-screen items, so the fixture stops at
         * what's actually measurable.
         */
        val MIXED_NAME_GAMES = listOf(
            IgdbGame.IgdbSimilarGame(id = 1L, name = "Bastion", coverImageId = null),
            IgdbGame.IgdbSimilarGame(id = 2L, name = "Divinity: Original Sin II Definitive", coverImageId = null),
            IgdbGame.IgdbSimilarGame(id = 3L, name = "Braid", coverImageId = null),
        )
    }
}
