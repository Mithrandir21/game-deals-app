package pm.bam.gamedeals.feature.home.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.models.Store.StoreImages
import pm.bam.gamedeals.feature.home.ui.DealSwipeViewModel.State
import pm.bam.gamedeals.feature.home.ui.DealSwipeViewModel.Status

/** The swipe deck's screen: the card, its buttons and gestures, and the screens around the deck. */
class DealSwipeScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val hades = deal("hades", "Hades")
    private val celeste = deal("celeste", "Celeste")
    private val stores = persistentMapOf(61 to Store(storeID = 61, storeName = "Steam", isActive = true, images = StoreImages("", "", "")))

    private val added = mutableListOf<Deal>()
    private val skipped = mutableListOf<Deal>()
    private val opened = mutableListOf<Deal>()
    private var undos = 0
    private var retries = 0
    private var signIns = 0

    private fun deal(gameId: String, title: String) = Deal(
        dealID = "d-$gameId", title = title, storeID = 61, gameID = gameId,
        salePriceValue = 5.0, salePriceDenominated = "$5.00", normalPriceValue = 20.0, normalPriceDenominated = "$20.00",
        isOnSale = true, savings = 75.0,
    )

    private fun show(initial: State): (State) -> Unit {
        var state by mutableStateOf(initial)
        composeTestRule.setContent {
            GameDealsTheme {
                DealSwipeContent(
                    state = state,
                    stores = stores,
                    onBack = {},
                    onOpen = { opened += it },
                    // Like the view model: the swiped card leaves the deck.
                    onSwipeRight = { added += it; state = state.copy(cards = persistentListOf(*state.cards.drop(1).toTypedArray())) },
                    onSwipeLeft = { skipped += it; state = state.copy(cards = persistentListOf(*state.cards.drop(1).toTypedArray())) },
                    onUndo = { undos++ },
                    onRetry = { retries++ },
                    onSignIn = { signIns++ },
                )
            }
        }
        return { state = it }
    }

    private fun ready(canUndo: Boolean = false, addedCount: Int = 0) =
        State(status = Status.READY, cards = persistentListOf(hades, celeste), canUndo = canUndo, addedCount = addedCount)

    @Test
    fun the_top_card_shows_the_deal_and_the_hint() {
        show(ready())

        composeTestRule.onNodeWithText("Hades").assertIsDisplayed()
        composeTestRule.onNodeWithText("$5.00").assertIsDisplayed()
        composeTestRule.onNodeWithText("Swipe right to waitlist, left to skip. Tap a card for details.").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Undo last swipe").assertIsNotEnabled()
    }

    @Test
    fun the_buttons_add_and_skip_the_top_card() {
        show(ready())

        composeTestRule.onNodeWithContentDescription("Add to waitlist").performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf(hades), added)

        composeTestRule.onNodeWithContentDescription("Skip").performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf(celeste), skipped)
    }

    @Test
    fun a_swipe_right_adds_and_a_swipe_left_skips() {
        show(ready())

        composeTestRule.onNodeWithTag(DEAL_SWIPE_CARD_TAG).performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()
        assertEquals(listOf(hades), added)

        composeTestRule.onNodeWithTag(DEAL_SWIPE_CARD_TAG).performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        assertEquals(listOf(celeste), skipped)
    }

    @Test
    fun a_short_slow_drag_puts_the_card_back() {
        show(ready())

        composeTestRule.onNodeWithTag(DEAL_SWIPE_CARD_TAG).performTouchInput {
            swipeRight(startX = centerX, endX = centerX + width * 0.1f, durationMillis = 800)
        }
        composeTestRule.waitForIdle()

        assertTrue(added.isEmpty())
        composeTestRule.onNodeWithText("Hades").assertIsDisplayed()
    }

    @Test
    fun a_tap_opens_the_game() {
        show(ready())

        composeTestRule.onNodeWithTag(DEAL_SWIPE_CARD_TAG).performClick()

        assertEquals(listOf(hades), opened)
    }

    @Test
    fun screen_readers_get_add_and_skip_as_actions_on_the_card() {
        show(ready())

        val actions = composeTestRule.onNodeWithTag(DEAL_SWIPE_CARD_TAG).fetchSemanticsNode()
            .config.getOrNull(SemanticsActions.CustomActions).orEmpty()
        assertEquals(listOf("Add to waitlist", "Skip"), actions.map { it.label })

        composeTestRule.runOnIdle { actions.first().action() }
        composeTestRule.waitForIdle()
        assertEquals(listOf(hades), added)
    }

    @Test
    fun undo_is_offered_after_a_swipe_and_the_count_replaces_the_hint() {
        show(ready(canUndo = true, addedCount = 2))

        composeTestRule.onNodeWithText("2 games added to your waitlist").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Undo last swipe").assertIsEnabled().performClick()
        assertEquals(1, undos)
    }

    @Test
    fun the_end_of_the_deck_says_so_and_keeps_undo() {
        show(State(status = Status.FINISHED, canUndo = true, addedCount = 1))

        composeTestRule.onNodeWithText("That's all of today's hot deals").assertIsDisplayed()
        composeTestRule.onNodeWithText("1 game added to your waitlist").assertIsDisplayed()
        composeTestRule.onNodeWithText("Undo last swipe").performClick()
        assertEquals(1, undos)
    }

    @Test
    fun a_failed_load_offers_a_retry_and_signed_out_offers_sign_in() {
        val set = show(State(status = Status.FAILED))
        composeTestRule.onNodeWithText("Try again").performClick()
        assertEquals(1, retries)

        set(State(status = Status.SIGNED_OUT))
        composeTestRule.onNodeWithText("Sign in").performClick()
        assertEquals(1, signIns)
    }

    @Test
    fun the_home_card_opens_the_deck() {
        var opens = 0
        composeTestRule.setContent { GameDealsTheme { DealSwipeHomeCard(onOpen = { opens++ }) } }

        composeTestRule.onNodeWithText("Swipe today's deals").performClick()

        assertEquals(1, opens)
    }
}
