package pm.bam.gamedeals.feature.home.ui

import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verify.VerifyMode.Companion.not
import dev.mokkery.verifySuspend
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.common.exceptions.HttpStatusFailure
import pm.bam.gamedeals.domain.models.AuthState
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.RepoUpdateResult
import pm.bam.gamedeals.domain.repositories.account.AccountRepository
import pm.bam.gamedeals.domain.repositories.dealswipe.DealSwipePage
import pm.bam.gamedeals.domain.repositories.dealswipe.DealSwipeRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.feature.home.ui.DealSwipeViewModel.Event
import pm.bam.gamedeals.feature.home.ui.DealSwipeViewModel.Status
import pm.bam.gamedeals.testing.MainDispatcherTest
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.utils.observeEmissions
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DealSwipeViewModelTest : MainDispatcherTest() {

    private class FakeDeck : DealSwipeRepository {
        /** Pages by offset; a missing offset ends the deck. */
        val pages = mutableMapOf<Int, DealSwipePage>()
        var failNext = false
        /** What the next failure throws; a plain "offline" error unless a test sets an HTTP answer. */
        var failure: Throwable? = null
        var gate: CompletableDeferred<Unit>? = null
        val asked = mutableListOf<Int>()
        val skipped = mutableListOf<String>()
        val unskipped = mutableListOf<String>()
        val describedIds = mutableListOf<String>()
        val descriptionsById = mutableMapOf<String, String>()

        override suspend fun nextPage(offset: Int): DealSwipePage {
            asked += offset
            gate?.await()
            if (failNext) {
                failNext = false
                throw failure ?: IllegalStateException("offline")
            }
            return pages[offset] ?: DealSwipePage(emptyList(), null)
        }

        override suspend fun skip(gameId: String) { skipped += gameId }
        override suspend fun unskip(gameId: String) { unskipped += gameId }
        override suspend fun description(deal: Deal): String? {
            describedIds += deal.gameID
            return descriptionsById[deal.gameID]
        }
    }

    private val authState = MutableStateFlow<AuthState>(AuthState.LoggedIn("bam"))
    private val accountRepository: AccountRepository = mock(MockMode.autoUnit) {
        every { observeAuthState() } returns authState
    }
    private val storesRepository: StoresRepository = mock {
        every { observeStores() } returns flowOf(emptyList())
    }
    private val waitlistIds = MutableStateFlow<ImmutableSet<String>>(persistentSetOf())
    private var toggleResult = RepoUpdateResult.UPDATED
    private var toggleGate: CompletableDeferred<Unit>? = null
    private val waitlistRepository: WaitlistRepository = mock(MockMode.autoUnit) {
        every { observeWaitlistIds() } returns waitlistIds
        everySuspend { toggleWaitlist(any()) } calls { (gameId: String) ->
            toggleGate?.await()
            if (toggleResult == RepoUpdateResult.UPDATED) {
                waitlistIds.value = (if (gameId in waitlistIds.value) waitlistIds.value - gameId else waitlistIds.value + gameId).toImmutableSet()
            }
            toggleResult
        }
    }
    private val deck = FakeDeck()

    @BeforeTest fun setUp() = installMainDispatcher()
    @AfterTest fun tearDown() = resetMainDispatcher()

    private fun deal(gameId: String) = Deal(
        dealID = "d-$gameId", title = "Game $gameId", storeID = 61, gameID = gameId,
        salePriceValue = 5.0, salePriceDenominated = "$5", normalPriceValue = 20.0, normalPriceDenominated = "$20",
        isOnSale = true, savings = 75.0,
    )

    private fun page(vararg ids: String, next: Int? = null) = DealSwipePage(ids.map(::deal), next)

    private fun viewModel() = DealSwipeViewModel(deck, waitlistRepository, storesRepository, accountRepository, TestingLoggingListener())

    private fun DealSwipeViewModel.top() = state.value.cards.first()

    private fun DealSwipeViewModel.ids() = state.value.cards.map { it.gameID }

    @Test
    fun the_deck_fills_from_the_first_pages_until_it_has_enough_cards() = runTest {
        deck.pages[0] = page("a", "b", "c", next = 50)
        deck.pages[50] = page("d", "e", "f", next = 100)
        deck.pages[100] = page("g", next = 150)

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(Status.READY, vm.state.value.status)
        assertEquals(listOf("a", "b", "c", "d", "e", "f"), vm.ids())
        assertEquals(listOf(0, 50), deck.asked, "stops once over the low-water mark")
    }

    @Test
    fun pages_with_nothing_new_are_passed_over_without_leaving_the_spinner() = runTest {
        deck.gate = CompletableDeferred()
        deck.pages[0] = page(next = 50)
        deck.pages[50] = page("a", next = null)

        val vm = viewModel()
        assertEquals(Status.LOADING, vm.state.value.status)
        deck.gate?.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("a"), vm.ids())
        assertEquals(Status.READY, vm.state.value.status)
    }

    @Test
    fun a_game_dealt_once_is_not_dealt_again_when_the_list_shifts() = runTest {
        deck.pages[0] = page("a", "b", next = 50)
        deck.pages[50] = page("b", "c", next = null)

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(listOf("a", "b", "c"), vm.ids())
    }

    @Test
    fun a_right_swipe_adds_the_game_to_the_waitlist() = runTest {
        deck.pages[0] = page("a", "b")
        val vm = viewModel()
        advanceUntilIdle()

        vm.onSwipeRight(vm.top())
        advanceUntilIdle()

        assertEquals(listOf("b"), vm.ids())
        assertTrue("a" in waitlistIds.value)
        assertEquals(1, vm.state.value.addedCount)
        assertTrue(vm.state.value.canUndo)
    }

    @Test
    fun a_game_already_on_the_waitlist_is_not_toggled_off() = runTest {
        deck.pages[0] = page("a", "b")
        waitlistIds.value = persistentSetOf("a")
        val vm = viewModel()
        advanceUntilIdle()

        vm.onSwipeRight(vm.top())
        advanceUntilIdle()

        verifySuspend(not) { waitlistRepository.toggleWaitlist(any()) }
        assertTrue("a" in waitlistIds.value)
        assertEquals(0, vm.state.value.addedCount)
    }

    @Test
    fun a_failed_add_offers_a_retry() = runTest {
        deck.pages[0] = page("a", "b")
        toggleResult = RepoUpdateResult.FAILED
        val vm = viewModel()
        advanceUntilIdle()
        val events = vm.events.observeEmissions(backgroundScope, testDispatcher)

        val a = vm.top()
        vm.onSwipeRight(a)
        advanceUntilIdle()
        assertEquals(listOf<Event>(Event.AddFailed(a)), events)

        toggleResult = RepoUpdateResult.UPDATED
        vm.onRetryAdd(a)
        advanceUntilIdle()
        assertTrue("a" in waitlistIds.value)
        assertEquals(1, vm.state.value.addedCount)
    }

    @Test
    fun undo_after_a_retried_add_takes_the_game_back_off_the_waitlist() = runTest {
        // Found on the emulator: offline add, Try again once back online, then Undo left the game on the waitlist.
        deck.pages[0] = page("a", "b")
        toggleResult = RepoUpdateResult.FAILED
        val vm = viewModel()
        advanceUntilIdle()

        val a = vm.top()
        vm.onSwipeRight(a)
        advanceUntilIdle()
        toggleResult = RepoUpdateResult.UPDATED
        vm.onRetryAdd(a)
        advanceUntilIdle()
        vm.onUndo()
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), vm.ids())
        assertFalse("a" in waitlistIds.value)
        assertEquals(0, vm.state.value.addedCount)
    }

    @Test
    fun retrying_an_add_whose_swipe_was_undone_adds_nothing() = runTest {
        deck.pages[0] = page("a", "b")
        toggleResult = RepoUpdateResult.FAILED
        val vm = viewModel()
        advanceUntilIdle()

        val a = vm.top()
        vm.onSwipeRight(a)
        advanceUntilIdle()
        vm.onUndo()
        advanceUntilIdle()
        toggleResult = RepoUpdateResult.UPDATED
        vm.onRetryAdd(a)
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), vm.ids(), "the card is back in the deck to swipe again")
        assertFalse("a" in waitlistIds.value)
    }

    @Test
    fun an_add_after_signing_out_shows_the_sign_in_state() = runTest {
        deck.pages[0] = page("a", "b")
        toggleResult = RepoUpdateResult.NOT_LOGGED_IN
        val vm = viewModel()
        advanceUntilIdle()

        vm.onSwipeRight(vm.top())
        advanceUntilIdle()

        assertEquals(Status.SIGNED_OUT, vm.state.value.status)
    }

    @Test
    fun a_left_swipe_skips_the_game_without_touching_the_waitlist() = runTest {
        deck.pages[0] = page("a", "b")
        val vm = viewModel()
        advanceUntilIdle()

        vm.onSwipeLeft(vm.top())
        advanceUntilIdle()

        assertEquals(listOf("b"), vm.ids())
        assertEquals(listOf("a"), deck.skipped)
        verifySuspend(not) { waitlistRepository.toggleWaitlist(any()) }
    }

    @Test
    fun a_swipe_for_a_card_that_is_not_on_top_is_ignored() = runTest {
        deck.pages[0] = page("a", "b")
        val vm = viewModel()
        advanceUntilIdle()
        val a = vm.top()

        vm.onSwipeLeft(a)
        vm.onSwipeLeft(a)
        vm.onSwipeRight(a)
        advanceUntilIdle()

        assertEquals(listOf("b"), vm.ids())
        assertEquals(listOf("a"), deck.skipped)
    }

    @Test
    fun undo_brings_back_a_skipped_card_and_takes_back_the_skip() = runTest {
        deck.pages[0] = page("a", "b")
        val vm = viewModel()
        advanceUntilIdle()

        vm.onSwipeLeft(vm.top())
        advanceUntilIdle()
        vm.onUndo()
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), vm.ids())
        assertEquals(listOf("a"), deck.unskipped)
        assertFalse(vm.state.value.canUndo, "one step only")
    }

    @Test
    fun undo_takes_a_game_back_off_the_waitlist_once_the_add_has_landed() = runTest {
        deck.pages[0] = page("a", "b")
        toggleGate = CompletableDeferred()
        val vm = viewModel()
        advanceUntilIdle()

        vm.onSwipeRight(vm.top())
        vm.onUndo()
        assertEquals(listOf("a", "b"), vm.ids(), "the card is back at once")

        toggleGate?.complete(Unit)
        advanceUntilIdle()

        assertFalse("a" in waitlistIds.value)
        assertEquals(0, vm.state.value.addedCount)
        verifySuspend(exactly(2)) { waitlistRepository.toggleWaitlist("a") }
    }

    @Test
    fun undo_pressed_twice_quickly_takes_back_once() = runTest {
        deck.pages[0] = page("a", "b", "c")
        val vm = viewModel()
        advanceUntilIdle()

        vm.onSwipeRight(vm.top())
        advanceUntilIdle()
        vm.onUndo()
        vm.onUndo()
        advanceUntilIdle()

        assertEquals(listOf("a", "b", "c"), vm.ids(), "the card comes back once")
        assertFalse("a" in waitlistIds.value)
        verifySuspend(exactly(2)) { waitlistRepository.toggleWaitlist("a") }
    }

    @Test
    fun a_swipe_while_the_first_page_is_still_loading_is_ignored() = runTest {
        deck.pages[0] = page("a", "b")
        deck.gate = CompletableDeferred()
        val vm = viewModel()
        runCurrent()

        vm.onSwipeRight(deal("a"))
        vm.onSwipeLeft(deal("a"))
        assertFalse(vm.state.value.canUndo)

        deck.gate?.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("a", "b"), vm.ids())
        verifySuspend(exactly(0)) { waitlistRepository.toggleWaitlist(any()) }
        assertTrue(deck.skipped.isEmpty())
    }

    @Test
    fun a_page_that_fails_because_a_library_list_failed_offers_retry() = runTest {
        deck.pages[0] = page("a")
        deck.failNext = true
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(DealSwipeViewModel.Status.FAILED, vm.state.value.status)

        vm.onRetry()
        advanceUntilIdle()

        assertEquals(listOf("a"), vm.ids())
    }

    @Test
    fun undo_leaves_a_game_that_was_already_on_the_waitlist() = runTest {
        deck.pages[0] = page("a", "b")
        waitlistIds.value = persistentSetOf("a")
        val vm = viewModel()
        advanceUntilIdle()

        vm.onSwipeRight(vm.top())
        vm.onUndo()
        advanceUntilIdle()

        assertTrue("a" in waitlistIds.value)
        verifySuspend(not) { waitlistRepository.toggleWaitlist(any()) }
    }

    @Test
    fun a_failed_undo_says_the_game_is_still_on_the_waitlist() = runTest {
        deck.pages[0] = page("a", "b")
        val vm = viewModel()
        advanceUntilIdle()
        val events = vm.events.observeEmissions(backgroundScope, testDispatcher)
        val a = vm.top()

        vm.onSwipeRight(a)
        advanceUntilIdle()
        toggleResult = RepoUpdateResult.FAILED
        vm.onUndo()
        advanceUntilIdle()

        assertEquals(listOf<Event>(Event.UndoFailed(a)), events)
    }

    @Test
    fun swiping_the_last_card_finishes_the_deck_and_undo_reopens_it() = runTest {
        deck.pages[0] = page("a")
        val vm = viewModel()
        advanceUntilIdle()

        vm.onSwipeLeft(vm.top())
        advanceUntilIdle()
        assertEquals(Status.FINISHED, vm.state.value.status)

        vm.onUndo()
        advanceUntilIdle()
        assertEquals(Status.READY, vm.state.value.status)
        assertEquals(listOf("a"), vm.ids())
    }

    @Test
    fun swiping_down_to_the_low_water_mark_fetches_the_next_page() = runTest {
        deck.pages[0] = page("a", "b", "c", "d", "e", "f", next = 50)
        deck.pages[50] = page("g", next = null)
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(listOf(0), deck.asked)

        vm.onSwipeLeft(vm.top())
        advanceUntilIdle()

        assertEquals(listOf(0, 50), deck.asked)
        assertEquals(listOf("b", "c", "d", "e", "f", "g"), vm.ids())
    }

    @Test
    fun a_failed_first_page_can_be_retried() = runTest {
        deck.failNext = true
        deck.pages[0] = page("a")
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(Status.FAILED, vm.state.value.status)

        vm.onRetry()
        advanceUntilIdle()

        assertEquals(Status.READY, vm.state.value.status)
        assertEquals(listOf("a"), vm.ids())
    }

    @Test
    fun a_rate_limited_first_page_says_itad_is_busy_and_retry_clears_it() = runTest {
        deck.failNext = true
        deck.failure = object : RuntimeException("HTTP 429"), HttpStatusFailure { override val httpStatus = 429 }
        deck.pages[0] = page("a")
        val vm = viewModel()
        advanceUntilIdle()
        assertEquals(Status.FAILED, vm.state.value.status)
        assertTrue(vm.state.value.itadBusy)

        vm.onRetry()
        advanceUntilIdle()

        assertEquals(Status.READY, vm.state.value.status)
        assertFalse(vm.state.value.itadBusy)
    }

    @Test
    fun other_failures_are_not_called_busy() = runTest {
        deck.failNext = true
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(Status.FAILED, vm.state.value.status)
        assertFalse(vm.state.value.itadBusy)
    }

    @Test
    fun a_failed_later_page_keeps_the_cards_and_tries_again_on_the_next_swipe() = runTest {
        deck.pages[0] = page("a", "b", "c", "d", "e", "f", next = 50)
        deck.pages[50] = page("g", next = null)
        val vm = viewModel()
        advanceUntilIdle()

        deck.failNext = true
        vm.onSwipeLeft(vm.top())
        advanceUntilIdle()
        assertEquals(Status.READY, vm.state.value.status)
        assertEquals(5, vm.state.value.cards.size)

        vm.onSwipeLeft(vm.top())
        advanceUntilIdle()
        assertEquals(listOf("c", "d", "e", "f", "g"), vm.ids())
    }

    @Test
    fun signing_out_clears_the_deck_and_signing_in_starts_it_again() = runTest {
        deck.pages[0] = page("a", "b")
        val vm = viewModel()
        advanceUntilIdle()

        authState.value = AuthState.LoggedOut
        advanceUntilIdle()
        assertEquals(Status.SIGNED_OUT, vm.state.value.status)
        assertTrue(vm.state.value.cards.isEmpty())

        authState.value = AuthState.LoggedIn("bam")
        advanceUntilIdle()
        assertEquals(listOf("a", "b"), vm.ids())
    }

    @Test
    fun the_top_three_cards_get_descriptions_each_asked_for_once() = runTest {
        deck.pages[0] = page("a", "b", "c", "d", "e", "f")
        deck.descriptionsById["a"] = "About a."
        deck.descriptionsById["d"] = "About d."
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(listOf("a", "b", "c"), deck.describedIds)
        assertEquals(mapOf("a" to "About a."), vm.descriptions.value)

        vm.onSwipeLeft(vm.top())
        advanceUntilIdle()
        vm.onUndo()
        advanceUntilIdle()

        assertEquals(listOf("a", "b", "c", "d"), deck.describedIds, "the undone card isn't asked about again")
        assertEquals(mapOf("a" to "About a.", "d" to "About d."), vm.descriptions.value)
    }

    @Test
    fun an_empty_hottest_list_finishes_at_once() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(Status.FINISHED, vm.state.value.status)
    }
}
