@file:OptIn(ExperimentalCoroutinesApi::class)

package pm.bam.gamedeals.feature.account.ui

import androidx.lifecycle.SavedStateHandle
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.domain.models.ItadNotification
import pm.bam.gamedeals.domain.models.NotificationDealGame
import pm.bam.gamedeals.domain.models.NotificationDetail
import pm.bam.gamedeals.domain.models.NotificationShopDeal
import pm.bam.gamedeals.domain.repositories.notifications.NotificationsRepository
import pm.bam.gamedeals.feature.account.ui.NotificationDayViewModel.NotificationDayEvent
import pm.bam.gamedeals.testing.MainDispatcherTest
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.utils.observeEmissions
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationDayViewModelTest : MainDispatcherTest() {

    private val notificationsRepository: NotificationsRepository = mock(MockMode.autoUnit)
    private val logger = TestingLoggingListener()

    @BeforeTest fun setUp() = installMainDispatcher()
    @AfterTest fun tearDown() = resetMainDispatcher()

    private fun viewModel(date: String? = DAY) =
        NotificationDayViewModel(
            savedStateHandle = SavedStateHandle(buildMap { date?.let { put("date", it) } }),
            notificationsRepository = notificationsRepository,
            logger = logger,
        )

    private fun notification(id: String, day: String = DAY, read: Boolean = false) =
        ItadNotification(id = id, type = "waitlist", title = "t$id", timestamp = "${day}T09:30:00+00:00", read = read)

    private fun deal(shop: String, price: Double) =
        NotificationShopDeal(shopName = shop, salePriceValue = price, salePriceDenominated = "$price", regularPriceDenominated = null, cutPercent = 50, url = "u")

    @Test
    fun open_aggregates_the_days_entries_and_tags_each_game_with_its_source_entry() = runTest {
        every { notificationsRepository.observeNotifications() } returns
            flowOf(listOf(notification("n1"), notification("n2"), notification("other", day = "2026-06-01")))
        everySuspend { notificationsRepository.getNotificationDetail("n1") } returns
            NotificationDetail("n1", listOf(NotificationDealGame(gameId = "g1", title = "Halo", deals = listOf(deal("GOG", 9.99), deal("Steam", 12.0)))))
        everySuspend { notificationsRepository.getNotificationDetail("n2") } returns
            NotificationDetail("n2", listOf(NotificationDealGame(gameId = "g2", title = "Hades", deals = listOf(deal("GOG", 5.0)))))

        val vm = viewModel()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.loading)
        assertEquals(listOf("g1", "g2"), vm.uiState.value.games.map { it.gameId })
        assertEquals("GOG", vm.uiState.value.games.first { it.gameId == "g1" }.bestDeal?.shopName)
        // The other-day entry is excluded.
        verifySuspend(exactly(0)) { notificationsRepository.getNotificationDetail("other") }
    }

    @Test
    fun a_game_referenced_by_two_entries_is_one_card_and_marks_both_read() = runTest {
        // Regression: a day with two entries for the SAME game must not produce duplicate LazyColumn keys.
        every { notificationsRepository.observeNotifications() } returns flowOf(listOf(notification("n1"), notification("n2")))
        everySuspend { notificationsRepository.getNotificationDetail("n1") } returns
            NotificationDetail("n1", listOf(NotificationDealGame(gameId = "g1", title = "Halo", deals = listOf(deal("GOG", 9.99)))))
        everySuspend { notificationsRepository.getNotificationDetail("n2") } returns
            NotificationDetail("n2", listOf(NotificationDealGame(gameId = "g1", title = "Halo", deals = listOf(deal("Steam", 8.0)))))

        val vm = viewModel()
        advanceUntilIdle()

        assertEquals(listOf("g1"), vm.uiState.value.games.map { it.gameId }) // one card despite two entries

        vm.onGameViewed("g1")
        advanceUntilIdle()

        verifySuspend(exactly(1)) { notificationsRepository.markRead("n1") }
        verifySuspend(exactly(1)) { notificationsRepository.markRead("n2") }
    }

    @Test
    fun viewing_a_game_marks_its_entry_read_once() = runTest {
        every { notificationsRepository.observeNotifications() } returns flowOf(listOf(notification("n1")))
        everySuspend { notificationsRepository.getNotificationDetail("n1") } returns
            NotificationDetail("n1", listOf(NotificationDealGame(gameId = "g1", title = "Halo", deals = listOf(deal("GOG", 9.99)))))

        val vm = viewModel()
        advanceUntilIdle()

        vm.onGameViewed("g1")
        vm.onGameViewed("g1") // idempotent
        advanceUntilIdle()

        verifySuspend(exactly(1)) { notificationsRepository.markRead("n1") }
    }

    @Test
    fun opening_the_day_does_not_mark_anything_read() = runTest {
        every { notificationsRepository.observeNotifications() } returns flowOf(listOf(notification("n1")))
        everySuspend { notificationsRepository.getNotificationDetail("n1") } returns
            NotificationDetail("n1", listOf(NotificationDealGame(gameId = "g1", title = "Halo", deals = listOf(deal("GOG", 9.99)))))

        val vm = viewModel()
        advanceUntilIdle()

        verifySuspend(exactly(0)) { notificationsRepository.markRead(any()) }
    }

    @Test
    fun expired_game_is_kept_in_state() = runTest {
        every { notificationsRepository.observeNotifications() } returns flowOf(listOf(notification("n1")))
        everySuspend { notificationsRepository.getNotificationDetail("n1") } returns
            NotificationDetail("n1", listOf(NotificationDealGame(gameId = "g1", title = "Halo", deals = emptyList())))

        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.games.single().isExpired)
    }

    @Test
    fun onOpenGame_emits_OpenGame() = runTest {
        every { notificationsRepository.observeNotifications() } returns flowOf(emptyList())

        val vm = viewModel()
        val events = vm.events.observeEmissions(this.backgroundScope, testDispatcher)
        advanceUntilIdle()

        vm.onOpenGame("g7")
        advanceUntilIdle()

        assertEquals(NotificationDayEvent.OpenGame("g7"), events.last())
    }

    @Test
    fun missing_date_finishes_loading_without_fetching() = runTest {
        val vm = viewModel(date = null)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.loading)
        verifySuspend(exactly(0)) { notificationsRepository.getNotificationDetail(any()) }
    }

    private companion object {
        const val DAY = "2026-06-18"
    }
}
