package pm.bam.gamedeals.feature.onboarding.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class NotificationStepTest {

    @Test
    fun no_choice_yet_offers_the_allow_or_decline_step() {
        // permissionGranted is irrelevant until the user has made a choice this run.
        assertEquals(
            NotificationStep.Choose,
            notificationStep(decided = false, declined = false, permissionGranted = false),
        )
        assertEquals(
            NotificationStep.Choose,
            notificationStep(decided = false, declined = false, permissionGranted = true),
        )
    }

    @Test
    fun allow_with_permission_granted_is_active() {
        assertEquals(
            NotificationStep.Active,
            notificationStep(decided = true, declined = false, permissionGranted = true),
        )
    }

    @Test
    fun allow_without_permission_is_blocked() {
        // Tapped Allow but the OS prompt was refused or suppressed — deep-link to settings.
        assertEquals(
            NotificationStep.Blocked,
            notificationStep(decided = true, declined = false, permissionGranted = false),
        )
    }

    @Test
    fun granting_via_settings_clears_blocked() {
        // Regression: returning from system settings with the permission granted must flip Blocked → Active,
        // since the live permission now wins.
        assertEquals(
            NotificationStep.Active,
            notificationStep(decided = true, declined = false, permissionGranted = true),
        )
    }

    @Test
    fun decline_is_declined_regardless_of_permission() {
        assertEquals(
            NotificationStep.Declined,
            notificationStep(decided = true, declined = true, permissionGranted = false),
        )
        assertEquals(
            NotificationStep.Declined,
            notificationStep(decided = true, declined = true, permissionGranted = true),
        )
    }
}
