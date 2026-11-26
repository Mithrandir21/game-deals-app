package pm.bam.gamedeals.feature.home.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.AnnotatedString
import kotlinx.collections.immutable.persistentListOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.BiggestDrop
import pm.bam.gamedeals.domain.models.CollectedGames
import pm.bam.gamedeals.domain.models.LongestWait
import pm.bam.gamedeals.domain.models.WaitlistWins
import pm.bam.gamedeals.domain.models.YearGame
import pm.bam.gamedeals.domain.models.YearInDeals

/** The shareable year card: what it says, the caption over the covers, and the highlighted numbers and titles. */
class YearInDealsCardTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val hades = YearGame("hades", "Hades")
    private val celeste = YearGame("celeste", "Celeste")

    private val year = YearInDeals(
        year = 2026,
        collected = CollectedGames(count = 12, highlights = persistentListOf(hades, celeste)),
        fullPriceValue = "$340",
        waitlistWins = WaitlistWins(count = 3, biggestDrop = BiggestDrop(hades, cutPercent = 80, lowDenominated = "$4.99", regularDenominated = "$24.99")),
        longestWait = LongestWait(celeste, days = 412),
    )

    private fun show(year: YearInDeals) {
        composeTestRule.setContent { GameDealsTheme { YearInDealsCard(year) } }
    }

    /** The text of the node showing exactly [text], with its styles. */
    private fun annotated(text: String): AnnotatedString =
        composeTestRule.onNodeWithText(text).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)!!.single()

    private fun AnnotatedString.colorOf(part: String): Color {
        val start = text.indexOf(part)
        return spanStyles.single { it.start == start && it.end == start + part.length }.item.color
    }

    @Test
    fun the_card_reads_as_plain_sentences() {
        show(year)

        composeTestRule.onNodeWithText("NEWEST IN YOUR COLLECTION").assertIsDisplayed()
        composeTestRule.onNodeWithText("12 games collected").assertIsDisplayed()
        composeTestRule.onNodeWithText("$340 at full price").assertIsDisplayed()
        composeTestRule.onNodeWithText("3 waitlist games hit their lowest price ever").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hades, 80% off, down to $4.99").assertIsDisplayed()
        composeTestRule.onNodeWithText("Celeste, on the waitlist for 412 days").assertIsDisplayed()
    }

    @Test
    fun numbers_share_one_colour_and_game_titles_another() {
        show(year)

        val drop = annotated("Hades, 80% off, down to $4.99")
        val number = drop.colorOf("80%")
        assertEquals(number, drop.colorOf("$4.99"))
        assertEquals(number, annotated("12 games collected").colorOf("12"))
        assertEquals(number, annotated("Celeste, on the waitlist for 412 days").colorOf("412"))
        assertNotEquals(number, drop.colorOf("Hades"))
        assertEquals(drop.colorOf("Hades"), annotated("Celeste, on the waitlist for 412 days").colorOf("Celeste"))
    }

    @Test
    fun no_covers_means_no_caption() {
        show(year.copy(collected = CollectedGames(count = 2, highlights = persistentListOf())))

        composeTestRule.onNodeWithText("2 games collected").assertIsDisplayed()
        composeTestRule.onNodeWithText("NEWEST IN YOUR COLLECTION").assertDoesNotExist()
    }
}
