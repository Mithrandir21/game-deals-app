@file:OptIn(ExperimentalCoroutinesApi::class)

package pm.bam.gamedeals.feature.account.ui

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
import pm.bam.gamedeals.domain.models.NotificationGame
import pm.bam.gamedeals.domain.repositories.notifications.NotificationsRepository
import pm.bam.gamedeals.feature.account.ui.NotificationsViewModel.NotificationsUiEvent
import pm.bam.gamedeals.testing.MainDispatcherTest
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.utils.observeEmissions
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotificationsViewModelTest : MainDispatcherTest() {

    private val notificationsRepository: NotificationsRepository = mock(MockMode.autoUnit)
    private val logger = TestingLoggingListener()

    @BeforeTest
    fun setUp() {
        installMainDispatcher()
        // Defaults so the VM's init (observe + reconcile) doesn't hit unstubbed calls; tests override.
        every { notificationsRepository.observeNotifications() } returns flowOf(emptyList())
        everySuspend { notificationsRepository.getNotifications() } returns emptyList()
    }

    @AfterTest fun tearDown() = resetMainDispatcher()

    private fun viewModel() = NotificationsViewModel(notificationsRepository, logger)

    private fun notification(id: String, type: String = "waitlist") =
        ItadNotification(id = id, type = type, title = id, timestamp = "2026-06-13T00:00:00+00:00", read = false)

    @Test
    fun single_game_waitlist_notification_marks_read_and_emits_OpenGame() = runTest {
        everySuspend { notificationsRepository.getWaitlistGames("n1") } returns listOf(NotificationGame("g1", "Halo"))

        val vm = viewModel()
        val events = vm.events.observeEmissions(this.backgroundScope, testDispatcher)

        vm.onNotificationClick(notification("n1"))
        advanceUntilIdle()

        assertEquals(NotificationsUiEvent.OpenGame("g1"), events.last())
        verifySuspend(exactly(1)) { notificationsRepository.markRead("n1") }
    }

    @Test
    fun multi_game_waitlist_notification_populates_chooser_without_emitting() = runTest {
        everySuspend { notificationsRepository.getWaitlistGames("n1") } returns
            listOf(NotificationGame("g1", "Halo"), NotificationGame("g2", "Hades"))

        val vm = viewModel()
        val events = vm.events.observeEmissions(this.backgroundScope, testDispatcher)

        vm.onNotificationClick(notification("n1"))
        advanceUntilIdle()

        assertEquals(2, vm.uiState.value.chooser.size)
        assertTrue(events.isEmpty())
    }

    @Test
    fun non_waitlist_notification_only_marks_read_and_does_not_fetch_detail() = runTest {
        val vm = viewModel()

        vm.onNotificationClick(notification("n1", type = "other"))
        advanceUntilIdle()

        verifySuspend(exactly(1)) { notificationsRepository.markRead("n1") }
        verifySuspend(exactly(0)) { notificationsRepository.getWaitlistGames(any()) }
    }

    @Test
    fun onChooserGameClick_emits_OpenGame_and_clears_the_chooser() = runTest {
        everySuspend { notificationsRepository.getWaitlistGames("n1") } returns
            listOf(NotificationGame("g1", "Halo"), NotificationGame("g2", "Hades"))

        val vm = viewModel()
        val events = vm.events.observeEmissions(this.backgroundScope, testDispatcher)

        vm.onNotificationClick(notification("n1"))
        advanceUntilIdle()
        vm.onChooserGameClick("g2")
        advanceUntilIdle()

        assertEquals(NotificationsUiEvent.OpenGame("g2"), events.last())
        assertTrue(vm.uiState.value.chooser.isEmpty())
    }
}
