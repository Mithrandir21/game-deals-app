@file:OptIn(ExperimentalCoroutinesApi::class)

package pm.bam.gamedeals.feature.account.ui

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.domain.repositories.notifications.NotificationSettings
import pm.bam.gamedeals.domain.scheduling.NotificationScheduler
import pm.bam.gamedeals.testing.MainDispatcherTest
import pm.bam.gamedeals.testing.utils.observeEmissions
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationSettingsViewModelTest : MainDispatcherTest() {

    private val settings: NotificationSettings = mock(MockMode.autoUnit) {
        every { observeEnabled() } returns flowOf(false)
    }
    private val scheduler: NotificationScheduler = mock(MockMode.autoUnit)

    @BeforeTest fun setUp() = installMainDispatcher()
    @AfterTest fun tearDown() = resetMainDispatcher()

    private fun viewModel() = NotificationSettingsViewModel(settings, scheduler)

    @Test
    fun enabled_reflects_the_settings_flag() = runTest {
        every { settings.observeEnabled() } returns flowOf(true)

        val emissions = viewModel().enabled.observeEmissions(this.backgroundScope, testDispatcher)

        assertEquals(true, emissions.last())
    }

    @Test
    fun onEnable_persists_the_flag_and_schedules_the_poll() = runTest {
        val vm = viewModel()
        vm.onEnable()
        advanceUntilIdle()

        verifySuspend(exactly(1)) { settings.setEnabled(true) }
        verify(exactly(1)) { scheduler.schedule() }
    }

    @Test
    fun onDisable_clears_the_flag_and_cancels_the_poll() = runTest {
        val vm = viewModel()
        vm.onDisable()
        advanceUntilIdle()

        verifySuspend(exactly(1)) { settings.setEnabled(false) }
        verify(exactly(1)) { scheduler.cancel() }
    }
}
