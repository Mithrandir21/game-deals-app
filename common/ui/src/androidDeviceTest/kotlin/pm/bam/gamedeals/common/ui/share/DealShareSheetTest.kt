package pm.bam.gamedeals.common.ui.share

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.common.ui.platform.LocalPlatformActions
import pm.bam.gamedeals.common.ui.platform.PlatformActions
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme

/** The card preview that opens before a deal is shared (matrix 3.2 and 3.4). */
class DealShareSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private class RecordingActions : PlatformActions {
        val texts = mutableListOf<String>()
        val images = mutableListOf<Pair<ByteArray, String>>()
        override fun share(text: String) { texts += text }
        override suspend fun shareImage(png: ByteArray, text: String) { images += png to text }
        override fun openInApp(url: String) = Unit
        override fun openAppNotificationSettings() = Unit
        override fun openStoreListing(storeId: String) = Unit
    }

    private val link = "https://gamedeals.bam.pm/g/018d937f-33f0-7200-80fc-87f769196c84"

    // No cover: offline, or art that never loaded, falls back to the placeholder art.
    private val card = DealShareCard(
        title = "Hades",
        coverUrl = null,
        priceDenominated = "$8.99",
        regularDenominated = "$24.99",
        cutPercent = 64,
        storeName = "Steam",
        isNewHistoricalLow = false,
        isLowestEver = true,
        isStoreLow = false,
        endsAtEpochMs = null,
        link = link,
    )
    private val share = DealShare(text = "Hades is 64% off on Steam: $8.99 $link", card = card)

    private fun show(actions: RecordingActions): DealSharer {
        lateinit var sharer: DealSharer
        composeTestRule.setContent {
            CompositionLocalProvider(LocalPlatformActions provides actions) {
                GameDealsTheme {
                    sharer = rememberDealSharer()
                    DealShareSheet(sharer)
                }
            }
        }
        return sharer
    }

    @Test
    fun the_preview_shows_the_deal_and_share_sends_the_image_with_the_link() {
        val actions = RecordingActions()
        val sharer = show(actions)
        composeTestRule.runOnIdle { sharer.share(share) }

        composeTestRule.onNodeWithText("Share this deal").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hades").assertIsDisplayed()
        composeTestRule.onNodeWithText("$8.99").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lowest price ever").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Found with Game Deals").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("bam.pm", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Share").performScrollTo().performClick()
        composeTestRule.waitUntil(5_000) { actions.images.isNotEmpty() }

        val (png, text) = actions.images.single()
        assertTrue("a real image", png.size > 100)
        assertTrue(text.contains(link))
        assertTrue(actions.texts.isEmpty())
        composeTestRule.onNodeWithText("Share this deal").assertDoesNotExist()
    }

    @Test
    fun the_preview_stays_open_when_the_phone_rotates() {
        // Found on the emulator: rotating the phone (or switching dark mode) closed the preview.
        val actions = RecordingActions()
        lateinit var sharer: DealSharer
        val restoration = StateRestorationTester(composeTestRule)
        restoration.setContent {
            CompositionLocalProvider(LocalPlatformActions provides actions) {
                GameDealsTheme {
                    sharer = rememberDealSharer()
                    DealShareSheet(sharer)
                }
            }
        }
        composeTestRule.runOnIdle { sharer.share(share.copy(card = card.copy(endsAtEpochMs = 1_796_000_000_000))) }
        composeTestRule.onNodeWithText("Share this deal").assertIsDisplayed()

        restoration.emulateSavedInstanceStateRestore()

        composeTestRule.onNodeWithText("Share this deal").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hades").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sale ends", substring = true).performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Share the link only").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf(share.text), actions.texts)
    }

    @Test
    fun share_the_link_only_sends_just_the_text() {
        val actions = RecordingActions()
        val sharer = show(actions)
        composeTestRule.runOnIdle { sharer.share(share) }

        composeTestRule.onNodeWithText("Share the link only").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf(share.text), actions.texts)
        assertTrue(actions.images.isEmpty())
        composeTestRule.onNodeWithText("Share this deal").assertDoesNotExist()
    }

    @Test
    fun a_share_without_a_card_goes_straight_to_the_share_sheet_as_text() {
        val actions = RecordingActions()
        val sharer = show(actions)
        composeTestRule.runOnIdle { sharer.share(DealShare(text = "Hades https://store.example/hades", card = null)) }
        composeTestRule.waitForIdle()

        assertEquals(listOf("Hades https://store.example/hades"), actions.texts)
        composeTestRule.onNodeWithText("Share this deal").assertDoesNotExist()
    }

    @Test
    fun a_card_without_an_end_date_shows_no_date_and_a_very_long_title_stays_within_two_lines() {
        val longTitle = "The Elder Scrolls V: Skyrim Special Edition – Anniversary Upgrade with Every Creation Club Item Ever Made"
        val actions = RecordingActions()
        val sharer = show(actions)
        composeTestRule.runOnIdle { sharer.share(share.copy(card = card.copy(title = longTitle))) }

        val layouts = mutableListOf<TextLayoutResult>()
        composeTestRule.onNodeWithText(longTitle).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.single().lineCount <= 2)
        assertTrue("shortened with an ellipsis", layouts.single().hasVisualOverflow)
        composeTestRule.onNodeWithText("Sale ends", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Share").performScrollTo().assertIsDisplayed()
    }
}
