@file:OptIn(ExperimentalCoroutinesApi::class)

package pm.bam.gamedeals.feature.onboarding.ui

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.domain.models.AuthState
import pm.bam.gamedeals.domain.models.Country
import pm.bam.gamedeals.domain.models.ItadUser
import pm.bam.gamedeals.domain.repositories.account.AccountRepository
import pm.bam.gamedeals.domain.repositories.notifications.NotificationSettings
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
import pm.bam.gamedeals.domain.repositories.settings.SettingsRepository
import pm.bam.gamedeals.domain.scheduling.NotificationScheduler
import pm.bam.gamedeals.feature.onboarding.platform.RegionDetector
import pm.bam.gamedeals.testing.MainDispatcherTest
import pm.bam.gamedeals.testing.TestingLoggingListener
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingViewModelTest : MainDispatcherTest() {

    private val us = Country("US", "United States")
    private val gb = Country("GB", "United Kingdom")

    private val regionRepository: RegionRepository = mock(MockMode.autoUnit)
    private val settingsRepository: SettingsRepository = mock(MockMode.autoUnit)
    private val notificationSettings: NotificationSettings = mock(MockMode.autoUnit)
    private val notificationScheduler: NotificationScheduler = mock(MockMode.autoUnit)
    private val accountRepository: AccountRepository = mock(MockMode.autoUnit)
    private val logger = TestingLoggingListener()

    // Real fun-interface instance driven by [detectedCountryCode] — simpler than mocking the detector.
    private var detectedCountryCode: String? = null
    private val regionDetector = RegionDetector { detectedCountryCode }

    @BeforeTest
    fun setUp() {
        installMainDispatcher()
        every { regionRepository.supportedCountries } returns listOf(us, gb)
        every { regionRepository.observeSelectedCountry() } returns flowOf(us)
        every { notificationSettings.observeEnabled() } returns flowOf(false)
        every { accountRepository.observeAuthState() } returns flowOf(AuthState.LoggedOut)
        // Default: a genuine first run (onboarding not yet completed).
        everySuspend { settingsRepository.getOnboardingCompleted() } returns false
    }

    @AfterTest fun tearDown() = resetMainDispatcher()

    private fun viewModel() = OnboardingViewModel(
        regionRepository,
        settingsRepository,
        notificationSettings,
        notificationScheduler,
        accountRepository,
        regionDetector,
        logger,
    )

    @Test
    fun detected_region_is_applied_on_first_run() = runTest {
        detectedCountryCode = "GB"

        viewModel()
        advanceUntilIdle()

        verifySuspend(exactly(1)) { regionRepository.setSelectedCountry(gb) }
    }

    @Test
    fun detected_region_is_matched_case_insensitively() = runTest {
        detectedCountryCode = "gb"

        viewModel()
        advanceUntilIdle()

        verifySuspend(exactly(1)) { regionRepository.setSelectedCountry(gb) }
    }

    @Test
    fun detected_region_ignored_when_onboarding_already_completed() = runTest {
        everySuspend { settingsRepository.getOnboardingCompleted() } returns true
        detectedCountryCode = "GB"

        viewModel()
        advanceUntilIdle()

        verifySuspend(exactly(0)) { regionRepository.setSelectedCountry(any()) }
    }

    @Test
    fun unsupported_detected_region_is_not_applied() = runTest {
        detectedCountryCode = "ZZ"

        viewModel()
        advanceUntilIdle()

        verifySuspend(exactly(0)) { regionRepository.setSelectedCountry(any()) }
    }

    @Test
    fun null_detected_region_is_not_applied() = runTest {
        detectedCountryCode = null

        viewModel()
        advanceUntilIdle()

        verifySuspend(exactly(0)) { regionRepository.setSelectedCountry(any()) }
    }

    @Test
    fun logged_in_session_is_reflected_in_state() = runTest {
        every { accountRepository.observeAuthState() } returns flowOf(AuthState.LoggedIn("bob"))

        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.loggedIn)
        assertEquals("bob", vm.uiState.value.username)
    }

    @Test
    fun logged_out_session_leaves_signed_in_state_false() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        assertFalse(vm.uiState.value.loggedIn)
    }

    @Test
    fun onCountrySelected_persists_region() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onCountrySelected(gb)
        advanceUntilIdle()

        verifySuspend(exactly(1)) { regionRepository.setSelectedCountry(gb) }
    }

    @Test
    fun onNotificationsEnabled_persists_opt_in_and_schedules_poll() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onNotificationsEnabled()
        advanceUntilIdle()

        verifySuspend(exactly(1)) { notificationSettings.setEnabled(true) }
        verify(exactly(1)) { notificationScheduler.schedule() }
    }

    @Test
    fun finish_marks_completed_then_invokes_callback() = runTest {
        val vm = viewModel()
        advanceUntilIdle()

        var finished = false
        vm.finish { finished = true }
        advanceUntilIdle()

        verifySuspend(exactly(1)) { settingsRepository.setOnboardingCompleted(true) }
        assertTrue(finished)
    }

    @Test
    fun signInThenFinish_marks_completed_logs_in_and_finishes() = runTest {
        everySuspend { accountRepository.login() } returns ItadUser("bob")
        val vm = viewModel()
        advanceUntilIdle()

        var finished = false
        vm.signInThenFinish { finished = true }
        advanceUntilIdle()

        verifySuspend(exactly(1)) { settingsRepository.setOnboardingCompleted(true) }
        verifySuspend(exactly(1)) { accountRepository.login() }
        assertTrue(finished)
        assertFalse(vm.uiState.value.signingIn)
    }
}
