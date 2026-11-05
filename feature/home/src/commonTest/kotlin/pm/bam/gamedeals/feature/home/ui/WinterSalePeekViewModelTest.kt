@file:OptIn(ExperimentalCoroutinesApi::class)

package pm.bam.gamedeals.feature.home.ui

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verify.VerifyMode.Companion.not
import dev.mokkery.verifySuspend
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.common.ui.deal.GamePeekSheetData
import pm.bam.gamedeals.common.ui.deal.StoreDealPair
import pm.bam.gamedeals.common.ui.share.DealShareTextBuilder
import pm.bam.gamedeals.domain.models.RepoUpdateResult
import pm.bam.gamedeals.domain.repositories.collection.CollectionRepository
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.ignored.IgnoredRepository
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.testing.MainDispatcherTest
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.fixtures.gameDeal
import pm.bam.gamedeals.testing.fixtures.gameDetails
import pm.bam.gamedeals.testing.fixtures.store
import pm.bam.gamedeals.testing.utils.observeEmissions
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WinterSalePeekViewModelTest : MainDispatcherTest() {

    private val steam = store(storeID = 61, storeName = "Steam")
    private val gog = store(storeID = 35, storeName = "GOG")

    private val gamesRepository: GamesRepository = mock(MockMode.autoUnit)
    private val storesRepository: StoresRepository = mock(MockMode.autoUnit) {
        every { observeStores() } returns flowOf(listOf(steam, gog))
        everySuspend { getStore(61) } returns steam
        everySuspend { getStore(35) } returns gog
        everySuspend { getStore(999) } returns null
    }
    private val waitlistIds = MutableStateFlow(persistentSetOf("w1"))
    private val waitlistRepository: WaitlistRepository = mock(MockMode.autoUnit) {
        every { observeWaitlistIds() } returns waitlistIds
    }
    private val collectionRepository: CollectionRepository = mock(MockMode.autoUnit) {
        every { observeCollectionIds() } returns flowOf(persistentSetOf("c1"))
    }
    private val ignoredRepository: IgnoredRepository = mock(MockMode.autoUnit) {
        every { observeIgnoredIds() } returns flowOf(persistentSetOf("i1"))
    }
    private val dealShareTextBuilder: DealShareTextBuilder = mock(MockMode.autoUnit)
    private val logger = TestingLoggingListener()

    @BeforeTest fun setUp() = installMainDispatcher()
    @AfterTest fun tearDown() = resetMainDispatcher()

    private fun viewModel() = WinterSalePeekViewModel(
        gamesRepository, storesRepository, waitlistRepository, collectionRepository, ignoredRepository, dealShareTextBuilder, logger,
    )

    @Test
    fun library_state_and_store_logos_follow_the_repositories() = runTest {
        val vm = viewModel()
        observeAll(vm)
        advanceUntilIdle()

        assertEquals(setOf("w1"), vm.waitlistIds.value)
        assertEquals(setOf("c1"), vm.collectionIds.value)
        assertEquals(setOf("i1"), vm.ignoredIds.value)
        assertEquals(mapOf(61 to steam, 35 to gog), vm.stores.value)

        waitlistIds.value = persistentSetOf("w1", "w2")
        advanceUntilIdle()
        assertEquals(setOf("w1", "w2"), vm.waitlistIds.value)
    }

    @Test
    fun a_failing_library_or_store_feed_leaves_the_rows_empty_instead_of_crashing() = runTest {
        every { waitlistRepository.observeWaitlistIds() } returns flow { throw IllegalStateException("db") }
        every { storesRepository.observeStores() } returns flow { throw IllegalStateException("db") }
        val vm = viewModel()
        observeAll(vm)
        advanceUntilIdle()

        assertTrue(vm.waitlistIds.value.isEmpty())
        assertTrue(vm.stores.value.isEmpty())
    }

    @Test
    fun peek_shows_loading_then_the_cheapest_store_first_and_drops_unknown_shops() = runTest {
        everySuspend { gamesRepository.getGameDetails("g1") } returns gameDetails(
            deals = persistentListOf(
                gameDeal(storeID = 35, dealID = "gog", priceValue = 12.0),
                gameDeal(storeID = 61, dealID = "steam", priceValue = 8.0),
                gameDeal(storeID = 999, dealID = "unknown", priceValue = 1.0),
            ),
        )
        val vm = viewModel()
        val states = vm.gamePeek.observeEmissions(backgroundScope, testDispatcher)

        vm.peek("g1", "Hades", "thumb")
        advanceUntilIdle()

        assertIs<GamePeekSheetData.Loading>(states[1])
        val data = assertIs<GamePeekSheetData.Data>(vm.gamePeek.value)
        assertEquals("steam", data.bestDeal?.deal?.dealID)
        assertEquals(listOf("gog"), data.otherStores.map { it.deal.dealID })
    }

    @Test
    fun a_failed_peek_shows_the_error_and_retry_loads_the_same_game_again() = runTest {
        everySuspend { gamesRepository.getGameDetails("g1") } throws IllegalStateException("offline")
        val vm = viewModel()

        vm.peek("g1", "Hades", "thumb")
        advanceUntilIdle()
        assertEquals(GamePeekSheetData.Error("g1", "Hades", "thumb"), vm.gamePeek.value)

        everySuspend { gamesRepository.getGameDetails("g1") } returns gameDetails(deals = persistentListOf(gameDeal(storeID = 61)))
        vm.retry()
        advanceUntilIdle()

        assertIs<GamePeekSheetData.Data>(vm.gamePeek.value)
        verifySuspend(exactly(2)) { gamesRepository.getGameDetails("g1") }
    }

    @Test
    fun retry_without_an_open_sheet_does_nothing_and_dismiss_closes_it() = runTest {
        everySuspend { gamesRepository.getGameDetails(any()) } returns gameDetails()
        val vm = viewModel()

        vm.retry()
        advanceUntilIdle()
        verifySuspend(not) { gamesRepository.getGameDetails(any()) }

        vm.peek("g1", "Hades", null)
        advanceUntilIdle()
        vm.dismiss()
        advanceUntilIdle()
        assertNull(vm.gamePeek.value)
    }

    @Test
    fun library_toggles_reach_their_repository() = runTest {
        everySuspend { waitlistRepository.toggleWaitlist("g1") } returns RepoUpdateResult.UPDATED
        everySuspend { collectionRepository.toggleCollection("g1") } returns RepoUpdateResult.UPDATED
        everySuspend { ignoredRepository.toggleIgnored("g1") } returns RepoUpdateResult.UPDATED
        val vm = viewModel()
        val events = vm.events.observeEmissions(backgroundScope, testDispatcher)

        vm.toggleWaitlist("g1")
        vm.toggleCollection("g1")
        vm.toggleIgnore("g1")
        advanceUntilIdle()

        verifySuspend(exactly(1)) { waitlistRepository.toggleWaitlist("g1") }
        verifySuspend(exactly(1)) { collectionRepository.toggleCollection("g1") }
        verifySuspend(exactly(1)) { ignoredRepository.toggleIgnored("g1") }
        assertTrue(events.isEmpty())
    }

    @Test
    fun a_signed_out_toggle_asks_to_sign_in_and_a_failed_one_says_so() = runTest {
        everySuspend { waitlistRepository.toggleWaitlist("g1") } returns RepoUpdateResult.NOT_LOGGED_IN
        everySuspend { collectionRepository.toggleCollection("g1") } returns RepoUpdateResult.FAILED
        val vm = viewModel()
        val events = vm.events.observeEmissions(backgroundScope, testDispatcher)

        vm.toggleWaitlist("g1")
        advanceUntilIdle()
        vm.toggleCollection("g1")
        advanceUntilIdle()

        assertEquals(listOf(WinterSalePeekViewModel.Event.SignInRequired, WinterSalePeekViewModel.Event.ActionFailed), events)
    }

    @Test
    fun share_uses_the_best_deal_and_does_nothing_without_one() = runTest {
        every { dealShareTextBuilder.build(any(), any(), any(), any()) } returns "Hades is $8 on Steam"
        val vm = viewModel()
        val events = vm.events.observeEmissions(backgroundScope, testDispatcher)

        vm.share(peekData(best = StoreDealPair(steam, gameDeal(storeID = 61, priceDenominated = "$8", url = "https://steam/hades"))))
        vm.share(peekData(best = null))
        advanceUntilIdle()

        assertEquals(listOf<WinterSalePeekViewModel.Event>(WinterSalePeekViewModel.Event.Share("Hades is $8 on Steam")), events)
        verify(exactly(1)) { dealShareTextBuilder.build("Hades", "$8", "Steam", "https://steam/hades") }
    }

    private fun kotlinx.coroutines.test.TestScope.observeAll(vm: WinterSalePeekViewModel) {
        vm.waitlistIds.observeEmissions(backgroundScope, testDispatcher)
        vm.collectionIds.observeEmissions(backgroundScope, testDispatcher)
        vm.ignoredIds.observeEmissions(backgroundScope, testDispatcher)
        vm.stores.observeEmissions(backgroundScope, testDispatcher)
    }

    private fun peekData(best: StoreDealPair?) = GamePeekSheetData.Data(
        gameId = "g1",
        gameName = "Hades",
        thumb = "thumb",
        bestDeal = best,
    )
}
