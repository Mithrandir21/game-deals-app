package pm.bam.gamedeals.notifications

import android.content.Intent

/**
 * Parses the deep-link extras written onto a tap [Intent] by [AndroidNotificationPresenter] back into a
 * [NotificationRoute] (the inverse of the presenter's `routeIntent`). Returns `null` when the intent carries
 * no — or an unrecognised — notification route: a normal launcher start. The two bundled summaries map to
 * the Notifications list and the Followed-series screen respectively.
 */
internal fun Intent.toNotificationRoute(): NotificationRoute? =
    when (getStringExtra(EXTRA_NOTIFICATION_ROUTE)) {
        ROUTE_NOTIFICATIONS -> NotificationRoute.Notifications
        ROUTE_FOLLOWED_SERIES -> NotificationRoute.FollowedSeries
        else -> null
    }
