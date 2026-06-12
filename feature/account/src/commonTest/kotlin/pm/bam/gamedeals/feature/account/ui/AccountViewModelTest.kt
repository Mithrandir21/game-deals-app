@file:OptIn(ExperimentalCoroutinesApi::class)

package pm.bam.gamedeals.feature.account.ui

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.domain.models.AuthState
import pm.bam.gamedeals.domain.models.ItadUser
import pm.bam.gamedeals.domain.repositories.account.AccountRepository
import pm.bam.gamedeals.domain.repositories.collection.CollectionRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.testing.MainDispatcherTest
import pm.bam.gamedeals.testing.TestingLoggingListener
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AccountViewModelTest : MainDispatcherTest() {

    private val accountRepository: AccountRepository = mock(MockMode.autoUnit)
    private val waitlistRepository: WaitlistRepository = mock(MockMode.autoUnit)
    private val collectionRepository: CollectionRepository = mock(MockMode.autoUnit)
    private val logger = TestingLoggingListener()

    @BeforeTest
    fun setUp() {
        installMainDispatcher()
        // Defaults so the VM's count observers + login reconcile don't hit unstubbed calls; tests override.
        every { waitlistRepository.observeWaitlistIds() } returns flowOf(persistentSetOf())
        every { collectionRepository.observeCollectionIds() } returns flowOf(persistentSetOf())
        everySuspend { waitlistRepository.getWaitlist() } returns emptyList()
        everySuspend { collectionRepository.getCollection() } returns emptyList()
    }

    @AfterTest fun tearDown() = resetMainDispatcher()

    private fun viewModel() = AccountViewModel(accountRepository, waitlistRepository, collectionRepository, logger)

    @Test
    fun logged_out_when_auth_state_is_logged_out() = runTest {
        every { accountRepository.observeAuthState() } returns flowOf(AuthState.LoggedOut)

        val vm = viewModel()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.loggedIn)
        assertEquals(0, vm.uiState.value.waitlistCount)
        assertEquals(0, vm.uiState.value.collectionCount)
    }

    @Test
    fun logged_in_loads_username_and_counts() = runTest {
        every { accountRepository.observeAuthState() } returns flowOf(AuthState.LoggedIn("bob"))
        every { waitlistRepository.observeWaitlistIds() } returns flowOf(persistentSetOf("a", "b"))
        every { collectionRepository.observeCollectionIds() } returns flowOf(persistentSetOf("c"))

        val vm = viewModel()
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.loggedIn)
        assertEquals("bob", state.username)
        assertEquals(2, state.waitlistCount)
        assertEquals(1, state.collectionCount)
    }

    @Test
    fun needs_reconnect_propagates_from_auth_state() = runTest {
        every { accountRepository.observeAuthState() } returns flowOf(AuthState.LoggedIn("bob", needsReconnect = true))

        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.needsReconnect)
    }

    @Test
    fun onLogin_invokes_repository_login() = runTest {
        every { accountRepository.observeAuthState() } returns flowOf(AuthState.LoggedOut)
        everySuspend { accountRepository.login() } returns ItadUser("bob")

        val vm = viewModel()
        advanceUntilIdle()
        vm.onLogin()
        advanceUntilIdle()

        verifySuspend(exactly(1)) { accountRepository.login() }
    }

    @Test
    fun onLogout_invokes_repository_logout() = runTest {
        every { accountRepository.observeAuthState() } returns flowOf(AuthState.LoggedOut)

        val vm = viewModel()
        advanceUntilIdle()
        vm.onLogout()
        advanceUntilIdle()

        verifySuspend(exactly(1)) { accountRepository.logout() }
    }
}
