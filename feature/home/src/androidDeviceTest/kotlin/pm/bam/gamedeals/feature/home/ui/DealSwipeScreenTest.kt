package pm.bam.gamedeals.feature.home.ui

import kotlinx.coroutines.CompletableDeferred
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
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
import kotlinx.collections.immutable.toImmutableMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import pm.bam.gamedeals.common.ui.signals.LocalGameSignals
import pm.bam.gamedeals.common.ui.theme.GameDealsTheme
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.GameSignals
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.models.Store.StoreImages
import pm.bam.gamedeals.domain.repositories.signals.GameSignalsRepository
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

    private fun show(
        initial: State,
        signals: Map<String, GameSignals> = emptyMap(),
        descriptions: Map<String, String> = emptyMap(),
    ): (State) -> Unit {
        var state by mutableStateOf(initial)
        val signalsSource = object : GameSignalsRepository {
            override suspend fun getSignals(gameIds: Collection<String>) = signals.filterKeys { it in gameIds }
        }
        composeTestRule.setContent {
            GameDealsTheme {
                CompositionLocalProvider(LocalGameSignals provides signalsSource) { DealSwipeContent(
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
                    descriptions = descriptions.toImmutableMap(),
                ) }
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
    fun the_card_says_what_the_game_is() {
        val hadesSignals = GameSignals(
            gameId = "hades", updatedEpochMs = 0, weeklyMaxCut = emptyList(),
            tags = listOf("Indie", "Action Roguelike", "Great Soundtrack", "Hack and Slash", "Mythology", "Isometric"),
            developer = "Supergiant Games", releaseYear = 2020,
        )
        show(ready(), signals = mapOf("hades" to hadesSignals), descriptions = mapOf("hades" to "Battle out of hell in this rogue-like dungeon crawler."))

        composeTestRule.onNodeWithText("Supergiant Games · 2020").assertIsDisplayed()
        composeTestRule.onNodeWithText("Action Roguelike").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hack and Slash").assertIsDisplayed()
        composeTestRule.onNodeWithText("Mythology").assertIsDisplayed()
        composeTestRule.onNodeWithText("Indie").assertDoesNotExist()
        composeTestRule.onNodeWithText("Isometric").assertDoesNotExist()
        composeTestRule.onNodeWithText("Battle out of hell in this rogue-like dungeon crawler.").assertIsDisplayed()
    }

    @Test
    fun a_card_without_details_shows_just_the_deal() {
        show(ready())

        composeTestRule.onNodeWithText("Hades").assertIsDisplayed()
        composeTestRule.onNodeWithText("· ", substring = true).assertDoesNotExist()
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
    fun a_rate_limited_load_says_itad_is_busy_instead_of_blaming_the_connection() {
        val set = show(State(status = Status.FAILED))
        composeTestRule.onNodeWithText("Check your connection", substring = true).assertIsDisplayed()

        set(State(status = Status.FAILED, itadBusy = true))
        composeTestRule.onNodeWithText("IsThereAnyDeal is busy right now. Try again in a minute.").assertIsDisplayed()
        composeTestRule.onNodeWithText("Check your connection", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Try again").assertIsDisplayed()
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

    // ---- Hardening (matrix 8.4)

    @Test
    fun a_long_title_and_description_stay_inside_the_card_on_a_small_screen_with_large_text() {
        val longTitle = "The Elder Scrolls V: Skyrim Special Edition – Anniversary Upgrade with Every Creation Club Item Ever Made"
        val longDescription = List(12) { "A very long description that keeps going and going." }.joinToString(" ")
        val skyrim = deal("skyrim", longTitle)
        val noSignals = object : GameSignalsRepository {
            override suspend fun getSignals(gameIds: Collection<String>) = emptyMap<String, GameSignals>()
        }
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f), LocalGameSignals provides noSignals) {
                GameDealsTheme {
                    Box(Modifier.size(320.dp, 560.dp)) {
                        DealSwipeContent(
                            state = State(status = Status.READY, cards = persistentListOf(skyrim, celeste)),
                            stores = stores, onBack = {}, onOpen = {}, onSwipeRight = {}, onSwipeLeft = {}, onUndo = {},
                            onRetry = {}, onSignIn = {}, descriptions = persistentMapOf("skyrim" to longDescription),
                        )
                    }
                }
            }
        }

        fun layout(text: String): TextLayoutResult {
            val layouts = mutableListOf<TextLayoutResult>()
            composeTestRule.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            return layouts.single()
        }
        assertTrue(layout(longTitle).lineCount <= 2)
        assertTrue(layout(longDescription).lineCount <= 3)
        // The buttons under the card stay on screen, below the description.
        composeTestRule.onNodeWithContentDescription("Add to waitlist").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Skip").assertIsDisplayed()
        val description = composeTestRule.onNodeWithText(longDescription).fetchSemanticsNode().boundsInRoot
        val add = composeTestRule.onNodeWithContentDescription("Add to waitlist").fetchSemanticsNode().boundsInRoot
        assertTrue("the description ends above the buttons", description.bottom <= add.top)
    }

    @Test
    fun a_phone_held_sideways_shows_the_title_and_price_beside_the_art() {
        // Found on the emulator: held sideways, the card showed only its art; the title, price and store were cut off.
        val noSignals = object : GameSignalsRepository {
            override suspend fun getSignals(gameIds: Collection<String>) = emptyMap<String, GameSignals>()
        }
        composeTestRule.setContent {
            CompositionLocalProvider(LocalGameSignals provides noSignals) {
                GameDealsTheme {
                    // A 360 dp phone in landscape, less the status bar. Required, since the test phone is narrower.
                    Box(Modifier.requiredSize(800.dp, 330.dp)) {
                        DealSwipeContent(
                            // Its own price, so the card underneath (also $5.00) can't be mistaken for it.
                            state = State(status = Status.READY, cards = persistentListOf(hades.copy(salePriceDenominated = "$4.99"), celeste)),
                            stores = stores, onBack = {}, onOpen = {}, onSwipeRight = {}, onSwipeLeft = {},
                            onUndo = {}, onRetry = {}, onSignIn = {}, descriptions = persistentMapOf("hades" to "Battle out of hell."),
                        )
                    }
                }
            }
        }

        val card = composeTestRule.onNodeWithTag(DEAL_SWIPE_CARD_TAG).fetchSemanticsNode().boundsInRoot
        for (text in listOf("Hades", "$4.99")) {
            // Unmerged: the card merges its text into one node for screen readers.
            val part = composeTestRule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("$text is inside the card", part.top >= card.top && part.bottom <= card.bottom)
        }
        val title = composeTestRule.onNodeWithText("Hades", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("the title sits beside the art, not under it", title.left > card.left + card.width / 3)
        val add = composeTestRule.onNodeWithContentDescription("Add to waitlist").fetchSemanticsNode().boundsInRoot
        assertTrue("the buttons stay under the card", add.top >= card.bottom)
        // No room for a description line on the short card, so none is cut in half.
        composeTestRule.onNodeWithText("Battle out of hell.", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun in_a_right_to_left_language_the_add_button_stays_on_the_side_a_right_swipe_goes() {
        // Found on the emulator: in Arabic the buttons swapped sides, but a swipe to the right still adds.
        val noSignals = object : GameSignalsRepository {
            override suspend fun getSignals(gameIds: Collection<String>) = emptyMap<String, GameSignals>()
        }
        composeTestRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalGameSignals provides noSignals) {
                GameDealsTheme {
                    DealSwipeContent(
                        state = ready(), stores = stores, onBack = {}, onOpen = {}, onSwipeRight = {}, onSwipeLeft = {},
                        onUndo = {}, onRetry = {}, onSignIn = {},
                    )
                }
            }
        }

        val add = composeTestRule.onNodeWithContentDescription("Add to waitlist").fetchSemanticsNode().boundsInRoot
        val skip = composeTestRule.onNodeWithContentDescription("Skip").fetchSemanticsNode().boundsInRoot
        assertTrue("Add stays on the right", add.left > skip.left)
    }

    @Test
    fun the_details_appear_when_the_signals_arrive_after_the_card() {
        val gate = CompletableDeferred<Unit>()
        val hadesSignals = GameSignals(
            gameId = "hades", updatedEpochMs = 0, weeklyMaxCut = emptyList(), developer = "Supergiant Games", releaseYear = 2020,
        )
        val slowSignals = object : GameSignalsRepository {
            override suspend fun getSignals(gameIds: Collection<String>): Map<String, GameSignals> {
                gate.await()
                return mapOf("hades" to hadesSignals).filterKeys { it in gameIds }
            }
        }
        composeTestRule.setContent {
            GameDealsTheme {
                CompositionLocalProvider(LocalGameSignals provides slowSignals) {
                    DealSwipeContent(
                        state = ready(), stores = stores, onBack = {}, onOpen = {}, onSwipeRight = {}, onSwipeLeft = {},
                        onUndo = {}, onRetry = {}, onSignIn = {},
                    )
                }
            }
        }
        composeTestRule.onNodeWithText("Hades").assertIsDisplayed()
        composeTestRule.onNodeWithText("Supergiant Games · 2020").assertDoesNotExist()

        gate.complete(Unit)

        composeTestRule.waitUntil(5_000) { composeTestRule.onAllNodesWithText("Supergiant Games · 2020").fetchSemanticsNodes().isNotEmpty() }
        composeTestRule.onNodeWithText("Supergiant Games · 2020").assertIsDisplayed()
    }
}
