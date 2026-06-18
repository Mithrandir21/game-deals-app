package pm.bam.gamedeals.notifications

import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pm.bam.gamedeals.domain.models.NotificationDealGame
import pm.bam.gamedeals.domain.repositories.notifications.PendingNotificationAlert

/**
 * Covers the two deep-link routing decisions (tap intent → route, and alert → tap destination): a tapped
 * notification always opens that notification's in-app detail screen (#272 follow-up).
 */
class NotificationRoutingTest {

    // --- Intent.toNotificationRoute(): parsing a tapped notification's extras ---

    private fun intent(route: String?, notificationId: String? = null): Intent = mockk {
        every { getStringExtra(EXTRA_NOTIFICATION_ROUTE) } returns route
        every { getStringExtra(EXTRA_NOTIFICATION_ID) } returns notificationId
    }

    @Test
    fun detail_route_with_an_id_parses_to_NotificationDetail() {
        assertEquals(
            NotificationRoute.NotificationDetail("n1"),
            intent(ROUTE_NOTIFICATION_DETAIL, notificationId = "n1").toNotificationRoute(),
        )
    }

    @Test
    fun detail_route_without_an_id_is_null() {
        assertNull(intent(ROUTE_NOTIFICATION_DETAIL, notificationId = null).toNotificationRoute())
    }

    @Test
    fun notifications_route_parses_to_Notifications() {
        assertEquals(NotificationRoute.Notifications, intent(ROUTE_NOTIFICATIONS).toNotificationRoute())
    }

    @Test
    fun an_absent_or_unrecognised_route_is_null() {
        assertNull(intent(route = null).toNotificationRoute())
        assertNull(intent(route = "something-else").toNotificationRoute())
    }

    // --- PendingNotificationAlert.toNotificationRoute(): where a delivered alert lands ---

    private fun alert(vararg games: NotificationDealGame) =
        PendingNotificationAlert("n1", "Price drop", games.toList())

    @Test
    fun an_alert_deep_links_to_its_detail_screen_regardless_of_game_count() {
        val expected = NotificationRoute.NotificationDetail("n1")
        assertEquals(expected, alert().toNotificationRoute())
        assertEquals(expected, alert(NotificationDealGame("g1", "Halo")).toNotificationRoute())
        assertEquals(
            expected,
            alert(NotificationDealGame("g1", "Halo"), NotificationDealGame("g2", "Hades")).toNotificationRoute(),
        )
    }
}
