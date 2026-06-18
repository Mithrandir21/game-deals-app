@file:OptIn(ExperimentalCoroutinesApi::class)

package pm.bam.gamedeals.feature.account.ui

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.domain.models.ItadNotification
import pm.bam.gamedeals.domain.repositories.notifications.NotificationsRepository
import pm.bam.gamedeals.testing.MainDispatcherTest
import pm.bam.gamedeals.testing.TestingLoggingListener
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationsViewModelTest : MainDispatcherTest() {

    private val notificationsRepository: NotificationsRepository = mock(MockMode.autoUnit)
    private val logger = TestingLoggingListener()

    @BeforeTest
    fun setUp() {
        installMainDispatcher()
        every { notificationsRepository.observeNotifications() } returns flowOf(emptyList())
        everySuspend { notificationsRepository.getNotifications() } returns emptyList()
    }

    @AfterTest fun tearDown() = resetMainDispatcher()

    private fun viewModel() = NotificationsViewModel(notificationsRepository, logger)

    private fun notification(id: String, read: Boolean = false) =
        ItadNotification(id = id, type = "waitlist", title = id, timestamp = "2026-06-13T00:00:00+00:00", read = read)

    @Test
    fun init_reconciles_and_reflects_observed_list() = runTest {
        every { notificationsRepository.observeNotifications() } returns
            flowOf(listOf(notification("n1", read = false), notification("n2", read = true)))

        val vm = viewModel()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.loading)
        assertEquals(2, vm.uiState.value.notifications.size)
        assertTrue(vm.uiState.value.hasUnread)
        verifySuspend(exactly(1)) { notificationsRepository.getNotifications() }
    }

    @Test
    fun onMarkAllRead_calls_remote() = runTest {
        val vm = viewModel()

        vm.onMarkAllRead()
        advanceUntilIdle()

        verifySuspend(exactly(1)) { notificationsRepository.markAllRead() }
    }
}
