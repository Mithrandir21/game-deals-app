package pm.bam.gamedeals.notifications

import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pm.bam.gamedeals.common.navigation.NotificationRoute

/**
 * Covers parsing a tapped notification's extras back into a route (#7 notification revamp): the bundled
 * waitlist summary opens the Notifications list, the bundled followed-franchise summary opens the
 * Followed-series screen, and anything else is ignored.
 */
class NotificationRoutingTest {

    private fun intent(route: String?): Intent = mockk {
        every { getStringExtra(EXTRA_NOTIFICATION_ROUTE) } returns route
    }

    @Test
    fun notifications_route_parses_to_Notifications() {
        assertEquals(NotificationRoute.Notifications, intent(ROUTE_NOTIFICATIONS).toNotificationRoute())
    }

    @Test
    fun followed_series_route_parses_to_FollowedSeries() {
        assertEquals(NotificationRoute.FollowedSeries, intent(ROUTE_FOLLOWED_SERIES).toNotificationRoute())
    }

    @Test
    fun an_absent_or_unrecognised_route_is_null() {
        assertNull(intent(route = null).toNotificationRoute())
        assertNull(intent(route = "something-else").toNotificationRoute())
    }
}
