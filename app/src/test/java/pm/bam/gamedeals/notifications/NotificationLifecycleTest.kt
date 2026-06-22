package pm.bam.gamedeals.notifications

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Test
import pm.bam.gamedeals.domain.models.AuthState
import pm.bam.gamedeals.domain.repositories.notifications.NotificationSettings
import pm.bam.gamedeals.domain.repositories.notifications.SurfacedNotificationStore
import pm.bam.gamedeals.domain.scheduling.NotificationScheduler
import pm.bam.gamedeals.domain.scheduling.applyNotificationLifecycle

/** The auth-state → poll reconciliation extracted from `GameDealsApplication` (Phase D). */
class NotificationLifecycleTest {

    private val settings: NotificationSettings = mockk(relaxed = true)
    private val scheduler: NotificationScheduler = mockk(relaxed = true)
    private val surfacedStore: SurfacedNotificationStore = mockk(relaxed = true)

    private suspend fun apply(state: AuthState) =
        applyNotificationLifecycle(state, settings, scheduler, surfacedStore)

    @Test
    fun login_with_opt_in_arms_the_poll() = runTest {
        coEvery { settings.isEnabled() } returns true

        apply(AuthState.LoggedIn("user"))

        verify(exactly = 1) { scheduler.schedule() }
        verify(exactly = 0) { scheduler.cancel() }
    }

    @Test
    fun login_without_opt_in_cancels_the_poll() = runTest {
        coEvery { settings.isEnabled() } returns false

        apply(AuthState.LoggedIn("user"))

        verify(exactly = 0) { scheduler.schedule() }
        verify(exactly = 1) { scheduler.cancel() }
    }

    @Test
    fun logout_with_opt_in_keeps_the_poll_for_franchise_alerts_and_clears_the_surfaced_set() = runTest {
        coEvery { settings.isEnabled() } returns true

        apply(AuthState.LoggedOut)

        // The poll runs logged-out for followed-franchise alerts (no ITAD account needed)…
        verify(exactly = 1) { scheduler.schedule() }
        verify(exactly = 0) { scheduler.cancel() }
        // …but the ITAD surfaced-id set is cleared so a different account re-alerts cleanly.
        coVerify(exactly = 1) { surfacedStore.replace(emptySet()) }
    }

    @Test
    fun logout_without_opt_in_cancels_the_poll_and_clears_the_surfaced_set() = runTest {
        coEvery { settings.isEnabled() } returns false

        apply(AuthState.LoggedOut)

        verify(exactly = 1) { scheduler.cancel() }
        coVerify(exactly = 1) { surfacedStore.replace(emptySet()) }
        verify(exactly = 0) { scheduler.schedule() }
    }
}
