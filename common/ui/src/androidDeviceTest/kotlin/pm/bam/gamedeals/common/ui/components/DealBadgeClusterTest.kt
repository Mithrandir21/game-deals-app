package pm.bam.gamedeals.common.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.common.ui.theme.allTimeLowBadgeContentDark
import pm.bam.gamedeals.common.ui.theme.allTimeLowBadgeContentLight
import pm.bam.gamedeals.common.ui.theme.darkScheme
import pm.bam.gamedeals.common.ui.theme.lightScheme
import pm.bam.gamedeals.common.ui.theme.newLowBadgeContainerDark
import pm.bam.gamedeals.common.ui.theme.newLowBadgeContainerLight
import pm.bam.gamedeals.common.ui.theme.topDiscountContainerDark
import pm.bam.gamedeals.common.ui.theme.topDiscountContainerLight
import kotlin.math.abs

/**
 * Device test for the shared badge group ([DealBadgeCluster]) and its chips: what shows for each mix of discount,
 * voucher and low-price flags, their order, and the colours actually drawn in the light and dark themes.
 */
class DealBadgeClusterTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setCluster(
        discountPercent: Int,
        hasVoucher: Boolean = false,
        isNewHistoricalLow: Boolean = false,
        isLowestEver: Boolean = false,
        isStoreLow: Boolean = false,
        onArt: Boolean = false,
    ) {
        composeTestRule.setContent {
            GameDealsTheme(darkTheme = false) {
                Box(Modifier.semantics { contentDescription = SLOT }) {
                    DealBadgeCluster(
                        discountPercent = discountPercent,
                        hasVoucher = hasVoucher,
                        isNewHistoricalLow = isNewHistoricalLow,
                        isLowestEver = isLowestEver,
                        isStoreLow = isStoreLow,
                        onArt = onArt,
                    )
                }
            }
        }
    }

    private fun slotWidth(): Float = composeTestRule.onNodeWithContentDescription(SLOT).fetchSemanticsNode().boundsInRoot.width

    private fun assertLetters(vararg shown: String) {
        listOf("N", "H", "S").forEach { letter ->
            composeTestRule.onAllNodesWithText(letter, useUnmergedTree = true).assertCountEquals(if (letter in shown) 1 else 0)
        }
    }

    @Test
    fun nothingShowsWithoutADiscountVoucherOrLetter() {
        setCluster(discountPercent = 0)

        assertEquals(0f, slotWidth())
    }

    @Test
    fun aVoucherAloneShowsTheScissorsButNoZeroDiscount() {
        setCluster(discountPercent = 0, hasVoucher = true)

        assertTrue("the voucher chip should take space", slotWidth() > 0f)
        composeTestRule.onNodeWithText("-0%", useUnmergedTree = true).assertDoesNotExist()
        assertLetters()
    }

    @Test
    fun aLetterAloneShowsWithoutADiscount() {
        setCluster(discountPercent = 0, isStoreLow = true)

        assertLetters("S")
        composeTestRule.onNodeWithText("-0%", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun aNewLowWinsOverAnAllTimeLowAndAStoreLow() {
        setCluster(discountPercent = 80, isNewHistoricalLow = true, isLowestEver = true, isStoreLow = true)

        assertLetters("N")
    }

    @Test
    fun anAllTimeLowWinsOverAStoreLow() {
        setCluster(discountPercent = 80, isLowestEver = true, isStoreLow = true)

        assertLetters("H")
    }

    @Test
    fun aDiscountWithoutFlagsShowsNoLetter() {
        setCluster(discountPercent = 30)

        composeTestRule.onNodeWithText("-30%", useUnmergedTree = true).assertIsDisplayed()
        assertLetters()
    }

    @Test
    fun voucherThenDiscountThenLetter() {
        setCluster(discountPercent = 80, hasVoucher = true, isNewHistoricalLow = true, isLowestEver = true)

        val slotLeft = composeTestRule.onNodeWithContentDescription(SLOT).fetchSemanticsNode().boundsInRoot.left
        val discount = composeTestRule.onNodeWithText("-80%", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val letter = composeTestRule.onNodeWithText("N", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val chipPadding = with(composeTestRule.density) { 4.dp.toPx() }
        assertTrue("the voucher should come before the discount", discount.left - slotLeft > chipPadding * 2)
        assertTrue("the letter should come after the discount", letter.left > discount.right)
    }

    @Test
    fun onArtTheChipsSitFurtherApartThanBesideAPrice() {
        setCluster(discountPercent = 80, isStoreLow = true, onArt = true)

        val discount = composeTestRule.onNodeWithText("-80%", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val letter = composeTestRule.onNodeWithText("S", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        // Each chip's 4dp side padding plus the 4dp gap used on art (2dp beside a price).
        val expected = with(composeTestRule.density) { 12.dp.toPx() }
        assertEquals(expected, letter.left - discount.right, 2f)
    }

    @Test
    fun aStandaloneDiscountSaysItsOwnDescription() {
        composeTestRule.setContent {
            GameDealsTheme(darkTheme = false) {
                DiscountBadge(discountPercent = 80, contentDescription = "80% off")
            }
        }

        composeTestRule.onNodeWithContentDescription("80% off").assertIsDisplayed()
    }

    @Test
    fun standaloneLettersSayTheirOwnDescriptions() {
        composeTestRule.setContent {
            GameDealsTheme(darkTheme = false) {
                Row {
                    NewHistoricalLowBadge(contentDescription = "new low")
                    AllTimeLowBadge(contentDescription = "all-time low")
                    StoreLowBadge(contentDescription = "store low")
                    VoucherBadge(contentDescription = "voucher")
                }
            }
        }

        listOf("new low", "all-time low", "store low", "voucher").forEach {
            composeTestRule.onNodeWithContentDescription(it).assertIsDisplayed()
        }
    }

    @Test
    fun discountChipsDrawTheirTierColourInTheLightTheme() {
        assertDiscountColours(dark = false)
    }

    @Test
    fun discountChipsDrawTheirTierColourInTheDarkTheme() {
        assertDiscountColours(dark = true)
    }

    @Test
    fun lettersDrawTheirColoursInTheLightTheme() {
        assertLetterColours(dark = false)
    }

    @Test
    fun lettersDrawTheirColoursInTheDarkTheme() {
        assertLetterColours(dark = true)
    }

    @Test
    fun discountColoursMatchWhatTheChipDraws() {
        var top: DiscountColors? = null
        var low: DiscountColors? = null
        composeTestRule.setContent {
            GameDealsTheme(darkTheme = true) {
                top = discountColors(90)
                low = discountColors(10)
            }
        }
        composeTestRule.waitForIdle()

        assertEquals(topDiscountContainerDark, top?.container)
        assertEquals(darkScheme.surfaceVariant, low?.container)
        assertEquals(darkScheme.onSurfaceVariant, low?.content)
    }

    private fun assertDiscountColours(dark: Boolean) {
        val scheme = if (dark) darkScheme else lightScheme
        val expected = linkedMapOf(
            10 to scheme.surfaceVariant,
            30 to scheme.secondaryContainer,
            60 to scheme.tertiaryContainer,
            80 to if (dark) topDiscountContainerDark else topDiscountContainerLight,
        )
        composeTestRule.setContent {
            GameDealsTheme(darkTheme = dark) {
                Column {
                    expected.keys.forEach { cut -> Chip("cut $cut") { DiscountBadge(discountPercent = cut) } }
                }
            }
        }
        expected.forEach { (cut, colour) -> assertTopEdge("cut $cut", colour) }
    }

    private fun assertLetterColours(dark: Boolean) {
        val scheme = if (dark) darkScheme else lightScheme
        composeTestRule.setContent {
            GameDealsTheme(darkTheme = dark) {
                Column {
                    Chip("N") { NewHistoricalLowBadge() }
                    Chip("H") { AllTimeLowBadge() }
                    Chip("S") { StoreLowBadge() }
                }
            }
        }
        assertTopEdge("N", if (dark) newLowBadgeContainerDark else newLowBadgeContainerLight)
        // H is an outline: its top edge is drawn in the label colour.
        assertTopEdge("H", if (dark) allTimeLowBadgeContentDark else allTimeLowBadgeContentLight)
        assertTopEdge("S", scheme.surfaceVariant)
    }

    @Composable
    private fun Chip(name: String, content: @Composable () -> Unit) {
        Box(Modifier.semantics { contentDescription = name }) { content() }
    }

    /** The colour along the middle of a chip's top edge: its fill, or its outline for H. */
    private fun assertTopEdge(name: String, expected: Color) {
        val image = composeTestRule.onNodeWithContentDescription(name).captureToImage().toPixelMap()
        val actual = image[image.width / 2, 1]
        val close = abs(actual.red - expected.red) < TOLERANCE &&
            abs(actual.green - expected.green) < TOLERANCE &&
            abs(actual.blue - expected.blue) < TOLERANCE
        assertTrue("$name chip draws $actual, expected $expected", close)
    }

    private companion object {
        const val SLOT = "badge slot"
        const val TOLERANCE = 0.06f
    }
}
