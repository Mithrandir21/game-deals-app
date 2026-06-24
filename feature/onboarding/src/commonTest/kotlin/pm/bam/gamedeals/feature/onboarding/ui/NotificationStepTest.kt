package pm.bam.gamedeals.feature.onboarding.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationStepTest {

    @Test
    fun active_only_when_opted_in_and_permission_granted() {
        assertEquals(
            NotificationStep.Active,
            notificationStep(enabled = true, permissionGranted = true, denied = false),
        )
    }

    @Test
    fun opted_in_but_permission_revoked_is_not_active() {
        // Regression: a permission revoked in settings must not keep reading as "on".
        assertEquals(
            NotificationStep.Blocked,
            notificationStep(enabled = true, permissionGranted = false, denied = true),
        )
        assertEquals(
            NotificationStep.Off,
            notificationStep(enabled = true, permissionGranted = false, denied = false),
        )
    }

    @Test
    fun permission_granted_but_opted_out_offers_enable() {
        assertEquals(
            NotificationStep.Enable,
            notificationStep(enabled = false, permissionGranted = true, denied = false),
        )
    }

    @Test
    fun granting_via_settings_clears_blocked_even_with_stale_denied_flag() {
        // Regression: after enabling in system settings the (sticky) denied flag may still be true, but the
        // live permission must win — show Enable (a one-tap opt-in), not the Blocked deep-link.
        assertEquals(
            NotificationStep.Enable,
            notificationStep(enabled = false, permissionGranted = true, denied = true),
        )
    }

    @Test
    fun permission_off_and_not_yet_refused_is_off() {
        assertEquals(
            NotificationStep.Off,
            notificationStep(enabled = false, permissionGranted = false, denied = false),
        )
    }

    @Test
    fun permission_off_after_refusal_is_blocked() {
        assertEquals(
            NotificationStep.Blocked,
            notificationStep(enabled = false, permissionGranted = false, denied = true),
        )
    }
}
