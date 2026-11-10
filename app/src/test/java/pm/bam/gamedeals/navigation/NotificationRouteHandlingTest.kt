package pm.bam.gamedeals.navigation

import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Test
import pm.bam.gamedeals.common.navigation.Destination
import pm.bam.gamedeals.common.navigation.NotificationRoute
import pm.bam.gamedeals.logging.analytics.Analytics
import pm.bam.gamedeals.logging.analytics.AnalyticsEvents

/** What a tapped background notification opens, and that every open is recorded. */
class NotificationRouteHandlingTest {

    private val analytics: Analytics = mockk(relaxed = true)
    private val navActions: NavigationActions = mockk(relaxed = true)

    @Test
    fun the_sale_reminder_opens_the_guide_while_the_event_is_on() = runTest {
        openNotificationRoute(NotificationRoute.WinterSaleGuide, analytics, navActions, winterSaleAvailable = { true })

        verify(exactly = 1) { navActions.navigateToWinterSaleGuide() }
        verify(exactly = 0) { navActions.navigateTopLevel(any()) }
        verify(exactly = 1) { analytics.capture(AnalyticsEvents.NOTIFICATION_OPENED, mapOf("route" to "winter_sale_guide")) }
    }

    @Test
    fun the_sale_reminder_lands_on_home_once_the_event_is_off() = runTest {
        openNotificationRoute(NotificationRoute.WinterSaleGuide, analytics, navActions, winterSaleAvailable = { false })

        verify(exactly = 0) { navActions.navigateToWinterSaleGuide() }
        verify(exactly = 1) { navActions.navigateTopLevel(Destination.Home) }
    }

    @Test
    fun the_waitlist_and_series_summaries_open_their_screens() = runTest {
        var checked = false
        openNotificationRoute(NotificationRoute.Notifications, analytics, navActions, winterSaleAvailable = { checked = true; true })
        openNotificationRoute(NotificationRoute.FollowedSeries, analytics, navActions, winterSaleAvailable = { checked = true; true })

        verify(exactly = 1) { navActions.navigateToNotifications() }
        verify(exactly = 1) { navActions.navigateToFollowedSeries() }
        verify(exactly = 1) { analytics.capture(AnalyticsEvents.NOTIFICATION_OPENED, mapOf("route" to "notifications")) }
        verify(exactly = 1) { analytics.capture(AnalyticsEvents.NOTIFICATION_OPENED, mapOf("route" to "followed_series")) }
        // Only the sale reminder needs the event check (a network call).
        assertFalse(checked)
    }
}
