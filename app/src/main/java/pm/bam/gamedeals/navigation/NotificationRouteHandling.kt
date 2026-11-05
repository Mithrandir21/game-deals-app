package pm.bam.gamedeals.navigation

import pm.bam.gamedeals.common.navigation.Destination
import pm.bam.gamedeals.common.navigation.NotificationRoute
import pm.bam.gamedeals.logging.analytics.Analytics
import pm.bam.gamedeals.logging.analytics.AnalyticsEvents

/**
 * Opens the screen for a tapped background notification and records the open. The sale reminder only opens the
 * guide while the event is still on (checked at tap time, since a reminder can outlive a remote disable); otherwise
 * it lands on Home rather than an "unavailable" guide.
 */
internal suspend fun openNotificationRoute(
    route: NotificationRoute,
    analytics: Analytics,
    navActions: NavigationActions,
    winterSaleAvailable: suspend () -> Boolean,
) {
    analytics.capture(AnalyticsEvents.NOTIFICATION_OPENED, mapOf("route" to route.analyticsName))
    when (route) {
        NotificationRoute.Notifications -> navActions.navigateToNotifications()
        NotificationRoute.FollowedSeries -> navActions.navigateToFollowedSeries()
        NotificationRoute.WinterSaleGuide ->
            if (winterSaleAvailable()) navActions.navigateToWinterSaleGuide() else navActions.navigateTopLevel(Destination.Home)
    }
}

private val NotificationRoute.analyticsName: String
    get() = when (this) {
        NotificationRoute.Notifications -> "notifications"
        NotificationRoute.FollowedSeries -> "followed_series"
        NotificationRoute.WinterSaleGuide -> "winter_sale_guide"
    }
