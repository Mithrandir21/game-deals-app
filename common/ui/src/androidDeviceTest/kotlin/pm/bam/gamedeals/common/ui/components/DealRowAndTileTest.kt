package pm.bam.gamedeals.common.ui.components

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.common.ui.PreviewDeal
import pm.bam.gamedeals.common.ui.generated.resources.Res
import pm.bam.gamedeals.common.ui.generated.resources.deal_all_time_low_content_suffix
import pm.bam.gamedeals.common.ui.generated.resources.deal_new_low_content_suffix
import pm.bam.gamedeals.common.ui.generated.resources.deal_owned_content_suffix
import pm.bam.gamedeals.common.ui.generated.resources.deal_store_low_content_suffix
import pm.bam.gamedeals.common.ui.generated.resources.deal_voucher_content_suffix
import pm.bam.gamedeals.common.ui.generated.resources.deal_waitlisted_content_suffix
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme

/**
 * Device test for the two shapes of the shared item look: the list row ([DealListRow]) keeps the badges beside
 * the price, the Featured tile ([DealHeroTile]) puts them on the art's corner; both say their badges to TalkBack.
 */
class DealRowAndTileTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var labels: Labels

    @Composable
    private fun loadLabels() {
        labels = Labels(
            newLow = stringResource(Res.string.deal_new_low_content_suffix),
            allTimeLow = stringResource(Res.string.deal_all_time_low_content_suffix),
            storeLow = stringResource(Res.string.deal_store_low_content_suffix),
            voucher = stringResource(Res.string.deal_voucher_content_suffix),
            waitlisted = stringResource(Res.string.deal_waitlisted_content_suffix),
            owned = stringResource(Res.string.deal_owned_content_suffix),
        )
    }

    private fun setRow(
        discountPercent: Int = 80,
        salePrice: String? = SALE,
        hasVoucher: Boolean = false,
        isNewHistoricalLow: Boolean = false,
        isLowestEver: Boolean = false,
        isStoreLow: Boolean = false,
        isWaitlisted: Boolean = false,
        isCollected: Boolean = false,
        neutralChip: String? = null,
        onClick: () -> Unit = {},
    ) {
        composeTestRule.setContent {
            loadLabels()
            GameDealsTheme(darkTheme = false) {
                DealListRow(
                    title = TITLE,
                    contentDescription = CD,
                    onClick = onClick,
                    salePrice = salePrice,
                    regularPrice = REGULAR,
                    neutralChip = neutralChip,
                    discountPercent = discountPercent,
                    hasVoucher = hasVoucher,
                    isNewHistoricalLow = isNewHistoricalLow,
                    isLowestEver = isLowestEver,
                    isStoreLow = isStoreLow,
                    storeName = STORE,
                    isWaitlisted = isWaitlisted,
                    isCollected = isCollected,
                )
            }
        }
    }

    private fun setTile(
        savings: Double = 78.0,
        storeName: String? = STORE,
        hasVoucher: Boolean = false,
        isNewHistoricalLow: Boolean = false,
        isLowestEver: Boolean = false,
        isStoreLow: Boolean = false,
        isWaitlisted: Boolean = false,
    ) {
        composeTestRule.setContent {
            loadLabels()
            GameDealsTheme(darkTheme = true) {
                DealHeroTile(
                    deal = PreviewDeal.copy(
                        title = TITLE,
                        salePriceDenominated = SALE,
                        normalPriceDenominated = REGULAR,
                        savings = savings,
                        hasVoucher = hasVoucher,
                        isNewHistoricalLow = isNewHistoricalLow,
                        isLowestEver = isLowestEver,
                        isStoreLow = isStoreLow,
                    ),
                    storeName = storeName,
                    contentDescription = CD,
                    onClick = {},
                    isWaitlisted = isWaitlisted,
                    modifier = Modifier.width(240.dp),
                )
            }
        }
    }

    private fun bounds(text: String) = composeTestRule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot

    // ---- Row ----

    @Test
    fun aRowsBadgesSitRightAboveItsPrice() {
        setRow(isLowestEver = true)

        val discount = bounds("-80%")
        val letter = bounds("H")
        val price = bounds(SALE)
        assertTrue("the discount should sit above the price", discount.bottom <= price.top)
        assertTrue("the letter should sit beside the discount", letter.left > discount.right && letter.top < price.top)
    }

    @Test
    fun aRowSaysItsLetterVoucherAndLibraryStateAfterItsOwnDescription() {
        setRow(hasVoucher = true, isLowestEver = true, isWaitlisted = true, isCollected = true)

        composeTestRule
            .onNodeWithContentDescription(CD + labels.allTimeLow + labels.voucher + labels.waitlisted + labels.owned)
            .assertIsDisplayed()
    }

    @Test
    fun aRowSaysEachLetterItCanShow() {
        setRow(isNewHistoricalLow = true, isLowestEver = true)
        composeTestRule.onNodeWithContentDescription(CD + labels.newLow).assertIsDisplayed()
    }

    @Test
    fun aStoreLowRowSaysSo() {
        setRow(isStoreLow = true)
        composeTestRule.onNodeWithContentDescription(CD + labels.storeLow).assertIsDisplayed()
    }

    @Test
    fun aRankedRowWithoutADealShowsOnlyItsPrice() {
        setRow(discountPercent = 0)

        composeTestRule.onNodeWithText(SALE, useUnmergedTree = true).assertIsDisplayed()
        listOf("N", "H", "S").forEach { composeTestRule.onAllNodesWithText(it, useUnmergedTree = true).assertCountEquals(0) }
        composeTestRule.onNodeWithContentDescription(CD).assertIsDisplayed()
    }

    @Test
    fun withoutAPriceTheNeutralChipTakesItsPlace() {
        setRow(discountPercent = 0, salePrice = null, neutralChip = "New release")

        composeTestRule.onNodeWithText("New release", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithText(REGULAR, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun tappingARowOpensIt() {
        var taps = 0
        setRow(onClick = { taps++ })

        composeTestRule.onNodeWithContentDescription(CD).performClick()

        assertEquals(1, taps)
    }

    // ---- Featured tile ----

    @Test
    fun aTilesBadgesSitOnTheArtsTopStartCorner() {
        setTile(hasVoucher = true, isNewHistoricalLow = true, isLowestEver = true)

        val tile = composeTestRule.onNodeWithContentDescription(CD + labels.newLow + labels.voucher).fetchSemanticsNode().boundsInRoot
        val discount = bounds("-78%")
        val letter = bounds("N")
        val title = bounds(TITLE)
        assertTrue("the badges should sit on the art, above the title", discount.bottom < title.top && letter.bottom < title.top)
        assertTrue("the badges should sit in the art's left half", letter.right < tile.left + tile.width / 2)
        // Under the title the tile shows the price, not the discount again.
        composeTestRule.onAllNodesWithText("-78%", useUnmergedTree = true).assertCountEquals(1)
        assertTrue("the price should sit under the title", bounds(SALE).top >= title.bottom)
    }

    @Test
    fun aTileShowsItsStoreUnderThePrice() {
        setTile(storeName = STORE)

        assertTrue(bounds(STORE).top >= bounds(SALE).bottom)
    }

    @Test
    fun aTileWithoutAStoreNameLeavesItOut() {
        setTile(storeName = null)

        composeTestRule.onNodeWithText(STORE, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun aTileAtItsAllTimeLowShowsHAndSaysIt() {
        setTile(isLowestEver = true, isWaitlisted = true)

        composeTestRule.onNodeWithText("H", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription(CD + labels.allTimeLow + labels.waitlisted).assertIsDisplayed()
    }

    @Test
    fun aTileRoundsItsCutBeforePickingTheTier() {
        // 74.6% reads as -75%, so it is drawn in the 75%+ tier like any other -75% chip.
        setTile(savings = 74.6)

        composeTestRule.onNodeWithText("-75%", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun aTileWithoutACutOrFlagsShowsNoBadges() {
        setTile(savings = 0.0)

        composeTestRule.onAllNodesWithText("-0%", useUnmergedTree = true).assertCountEquals(0)
        listOf("N", "H", "S").forEach { composeTestRule.onAllNodesWithText(it, useUnmergedTree = true).assertCountEquals(0) }
    }

    private data class Labels(
        val newLow: String,
        val allTimeLow: String,
        val storeLow: String,
        val voucher: String,
        val waitlisted: String,
        val owned: String,
    )

    private companion object {
        const val TITLE = "Resident Evil 4"
        const val CD = "Resident Evil 4, on sale for $8.60, was $39.99"
        const val SALE = "$8.60"
        const val REGULAR = "$39.99"
        const val STORE = "GreenManGaming"
    }
}
