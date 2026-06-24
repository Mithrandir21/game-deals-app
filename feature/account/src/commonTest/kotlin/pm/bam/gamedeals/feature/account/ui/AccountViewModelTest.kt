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
import pm.bam.gamedeals.domain.models.Country
import pm.bam.gamedeals.domain.models.ItadUser
import pm.bam.gamedeals.domain.repositories.account.AccountRepository
import pm.bam.gamedeals.domain.repositories.collection.CollectionRepository
import pm.bam.gamedeals.domain.repositories.notifications.NotificationsRepository
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
import pm.bam.gamedeals.domain.repositories.settings.SettingsRepository
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
    private val regionRepository: RegionRepository = mock(MockMode.autoUnit)
    private val settingsRepository: SettingsRepository = mock(MockMode.autoUnit)
    private val notificationsRepository: NotificationsRepository = mock(MockMode.autoUnit)
    private val logger = TestingLoggingListener()

    @BeforeTest
    fun setUp() {
        installMainDispatcher()
        // Defaults so the VM's count/region/notification observers don't hit unstubbed calls; tests override.
        // The library reconcile is now owned app-wide (applyLibraryLifecycle), so the VM no longer fetches here.
        every { waitlistRepository.observeWaitlistIds() } returns flowOf(persistentSetOf())
        every { collectionRepository.observeCollectionIds() } returns flowOf(persistentSetOf())
        every { regionRepository.supportedCountries } returns listOf(Country("US", "United States"))
        every { regionRepository.observeSelectedCountry() } returns flowOf(Country("US", "United States"))
        every { settingsRepository.observeMatureOptIn() } returns flowOf(false)
        every { settingsRepository.observeAnalyticsConsent() } returns flowOf(false)
        every { notificationsRepository.observeUnreadCount() } returns flowOf(0)
    }

    @AfterTest fun tearDown() = resetMainDispatcher()

    private fun viewModel() = AccountViewModel(accountRepository, waitlistRepository, collectionRepository, regionRepository, settingsRepository, notificationsRepository, logger)

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

    @Test
    fun mature_opt_in_is_observed_into_state() = runTest {
        every { accountRepository.observeAuthState() } returns flowOf(AuthState.LoggedOut)
        every { settingsRepository.observeMatureOptIn() } returns flowOf(true)

        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.matureOptIn)
    }

    @Test
    fun onSetMatureOptIn_persists_to_settings_repository() = runTest {
        every { accountRepository.observeAuthState() } returns flowOf(AuthState.LoggedOut)

        val vm = viewModel()
        advanceUntilIdle()
        vm.onSetMatureOptIn(true)
        advanceUntilIdle()

        verifySuspend(exactly(1)) { settingsRepository.setMatureOptIn(true) }
    }

    @Test
    fun analytics_consent_is_observed_into_state() = runTest {
        every { accountRepository.observeAuthState() } returns flowOf(AuthState.LoggedOut)
        every { settingsRepository.observeAnalyticsConsent() } returns flowOf(true)

        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.analyticsConsent)
    }

    @Test
    fun onSetAnalyticsConsent_persists_to_settings_repository() = runTest {
        every { accountRepository.observeAuthState() } returns flowOf(AuthState.LoggedOut)

        val vm = viewModel()
        advanceUntilIdle()
        vm.onSetAnalyticsConsent(true)
        advanceUntilIdle()

        verifySuspend(exactly(1)) { settingsRepository.setAnalyticsConsent(true) }
    }
}
