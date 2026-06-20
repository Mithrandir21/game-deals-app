package pm.bam.gamedeals.notifications

import android.content.Intent
import pm.bam.gamedeals.domain.repositories.notifications.PendingNotificationAlert

/**
 * Parses the deep-link extras written onto a tap [Intent] by [AndroidNotificationPresenter] back into a
 * [NotificationRoute] (the inverse of the presenter's `routeIntent`). Returns `null` when the intent carries
 * no — or an unrecognised — notification route: a normal launcher start, or a [ROUTE_NOTIFICATION_DETAIL]
 * tap that somehow arrived without its notification id.
 */
internal fun Intent.toNotificationRoute(): NotificationRoute? =
    when (getStringExtra(EXTRA_NOTIFICATION_ROUTE)) {
        ROUTE_NOTIFICATION_DETAIL -> getStringExtra(EXTRA_NOTIFICATION_ID)?.let { NotificationRoute.NotificationDetail(it) }
        ROUTE_GAME -> getStringExtra(EXTRA_GAME_ID)?.let { NotificationRoute.Game(it) }
        ROUTE_NOTIFICATIONS -> NotificationRoute.Notifications
        else -> null
    }

/**
 * Where tapping a delivered alert should land the user: a followed-franchise deal alert carries a [gameId]
 * and opens that game's page directly; a per-notification ITAD alert opens that notification's in-app
 * detail screen (the deals inside it).
 */
internal fun PendingNotificationAlert.toNotificationRoute(): NotificationRoute =
    gameId?.let { NotificationRoute.Game(it) } ?: NotificationRoute.NotificationDetail(notificationId)
