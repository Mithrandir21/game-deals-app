package pm.bam.gamedeals.feature.account.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationDeliveryStateTest {

    @Test
    fun active_only_when_opted_in_and_permission_granted() {
        assertTrue(backgroundAlertsActive(optedIn = true, permissionGranted = true))
        assertFalse(backgroundAlertsActive(optedIn = true, permissionGranted = false))
        assertFalse(backgroundAlertsActive(optedIn = false, permissionGranted = true))
        assertFalse(backgroundAlertsActive(optedIn = false, permissionGranted = false))
    }

    @Test
    fun blocked_when_opted_in_but_permission_off() {
        assertTrue(backgroundAlertsBlocked(optedIn = true, permissionGranted = false, permissionDenied = false))
    }

    @Test
    fun blocked_when_in_app_request_refused() {
        assertTrue(backgroundAlertsBlocked(optedIn = false, permissionGranted = false, permissionDenied = true))
    }

    @Test
    fun not_blocked_once_permission_granted_even_with_stale_denied_flag() {
        // Regression: returning from system settings with the permission granted must clear the rationale,
        // even though the sticky in-app denial flag is still set.
        assertFalse(backgroundAlertsBlocked(optedIn = true, permissionGranted = true, permissionDenied = true))
        assertFalse(backgroundAlertsBlocked(optedIn = false, permissionGranted = true, permissionDenied = true))
    }

    @Test
    fun not_blocked_before_any_intent() {
        assertFalse(backgroundAlertsBlocked(optedIn = false, permissionGranted = false, permissionDenied = false))
    }
}
