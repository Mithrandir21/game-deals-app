package pm.bam.gamedeals.notifications

import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pm.bam.gamedeals.domain.models.NotificationGame
import pm.bam.gamedeals.domain.repositories.notifications.PendingNotificationAlert

/**
 * Covers the two deep-link routing decisions extracted from `MainActivity` (tap intent → route) and
 * `AndroidNotificationPresenter` (alert → tap destination) — see #288.
 */
class NotificationRoutingTest {

    // --- Intent.toNotificationRoute(): parsing a tapped notification's extras ---

    private fun intent(route: String?, gameId: String? = null): Intent = mockk {
        every { getStringExtra(EXTRA_NOTIFICATION_ROUTE) } returns route
        every { getStringExtra(EXTRA_NOTIFICATION_GAME_ID) } returns gameId
    }

    @Test
    fun game_route_with_an_id_parses_to_Game() {
        assertEquals(NotificationRoute.Game("g1"), intent(ROUTE_GAME, gameId = "g1").toNotificationRoute())
    }

    @Test
    fun game_route_without_an_id_is_null() {
        assertNull(intent(ROUTE_GAME, gameId = null).toNotificationRoute())
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

    private fun alert(vararg games: NotificationGame) =
        PendingNotificationAlert("n1", "Price drop", games.toList())

    @Test
    fun a_single_game_alert_deep_links_to_that_game() {
        assertEquals(
            NotificationRoute.Game("g1"),
            alert(NotificationGame("g1", "Halo")).toNotificationRoute(),
        )
    }

    @Test
    fun a_multi_game_alert_opens_the_notifications_screen() {
        assertEquals(
            NotificationRoute.Notifications,
            alert(NotificationGame("g1", "Halo"), NotificationGame("g2", "Hades")).toNotificationRoute(),
        )
    }

    @Test
    fun a_no_game_alert_opens_the_notifications_screen() {
        assertEquals(NotificationRoute.Notifications, alert().toNotificationRoute())
    }
}
