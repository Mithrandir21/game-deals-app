@file:OptIn(ExperimentalCoroutinesApi::class)

package pm.bam.gamedeals.feature.account.ui

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verify.VerifyMode.Companion.not
import dev.mokkery.verifySuspend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.domain.models.AuthState
import pm.bam.gamedeals.domain.repositories.account.AccountRepository
import pm.bam.gamedeals.domain.repositories.notifications.NotificationsRepository
import pm.bam.gamedeals.testing.MainDispatcherTest
import pm.bam.gamedeals.testing.utils.observeEmissions
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Both exposed flows are `stateIn(WhileSubscribed)`, so they stay on their initial value until something
 * collects — every assertion goes through [observeEmissions] on `backgroundScope` rather than `.value`.
 */
class NotificationBellViewModelTest : MainDispatcherTest() {

    private val notificationsRepository: NotificationsRepository = mock(MockMode.autoUnit)
    private val accountRepository: AccountRepository = mock(MockMode.autoUnit)

    @BeforeTest
    fun setUp() {
        installMainDispatcher()
        every { notificationsRepository.observeUnreadGameCount() } returns flowOf(0)
        everySuspend { notificationsRepository.getNotifications() } returns emptyList()
    }

    @AfterTest fun tearDown() = resetMainDispatcher()

    private fun viewModel() = NotificationBellViewModel(notificationsRepository, accountRepository)

    @Test
    fun bell_is_hidden_when_logged_out() = runTest {
        every { accountRepository.observeAuthState() } returns flowOf(AuthState.LoggedOut)

        val vm = viewModel()
        val visible = vm.loggedIn.observeEmissions(backgroundScope, testDispatcher)
        advanceUntilIdle()

        assertFalse(visible.last())
    }

    /**
     * The case the count-only flow could not express: `observeUnreadGameCount()` emits 0 both here and when
     * logged out, so only the auth read separates "no bell" from "plain, unbadged bell".
     */
    @Test
    fun bell_is_visible_with_no_badge_when_logged_in_and_nothing_unread() = runTest {
        every { accountRepository.observeAuthState() } returns flowOf(AuthState.LoggedIn("tester"))
        every { notificationsRepository.observeUnreadGameCount() } returns flowOf(0)

        val vm = viewModel()
        val visible = vm.loggedIn.observeEmissions(backgroundScope, testDispatcher)
        val counts = vm.unreadCount.observeEmissions(backgroundScope, testDispatcher)
        advanceUntilIdle()

        assertTrue(visible.last())
        assertEquals(0, counts.last())
    }

    @Test
    fun bell_is_visible_with_the_unread_tally_when_logged_in() = runTest {
        every { accountRepository.observeAuthState() } returns flowOf(AuthState.LoggedIn("tester"))
        every { notificationsRepository.observeUnreadGameCount() } returns flowOf(3)

        val vm = viewModel()
        val visible = vm.loggedIn.observeEmissions(backgroundScope, testDispatcher)
        val counts = vm.unreadCount.observeEmissions(backgroundScope, testDispatcher)
        advanceUntilIdle()

        assertTrue(visible.last())
        assertEquals(3, counts.last())
    }

    @Test
    fun notifications_and_unread_games_are_resolved_once_on_login() = runTest {
        every { accountRepository.observeAuthState() } returns flowOf(AuthState.LoggedIn("tester"))

        viewModel()
        advanceUntilIdle()

        verifySuspend(exactly(1)) { notificationsRepository.getNotifications() }
        verifySuspend(exactly(1)) { notificationsRepository.resolveUnreadGames() }
    }

    @Test
    fun notifications_are_not_refreshed_while_logged_out() = runTest {
        every { accountRepository.observeAuthState() } returns flowOf(AuthState.LoggedOut)

        viewModel()
        advanceUntilIdle()

        verifySuspend(not) { notificationsRepository.getNotifications() }
        verifySuspend(not) { notificationsRepository.resolveUnreadGames() }
    }

    /** A failing refresh must not take the bell down with it — the tally still flows. */
    @Test
    fun a_failing_refresh_leaves_the_bell_visible() = runTest {
        every { accountRepository.observeAuthState() } returns flowOf(AuthState.LoggedIn("tester"))
        everySuspend { notificationsRepository.getNotifications() } throws IllegalStateException("network down")
        every { notificationsRepository.observeUnreadGameCount() } returns flowOf(2)

        val vm = viewModel()
        val visible = vm.loggedIn.observeEmissions(backgroundScope, testDispatcher)
        val counts = vm.unreadCount.observeEmissions(backgroundScope, testDispatcher)
        advanceUntilIdle()

        assertTrue(visible.last())
        assertEquals(2, counts.last())
    }
}
