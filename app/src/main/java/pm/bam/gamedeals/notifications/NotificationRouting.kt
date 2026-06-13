package pm.bam.gamedeals.notifications

import android.content.Intent
import pm.bam.gamedeals.domain.repositories.notifications.PendingNotificationAlert

/**
 * Parses the deep-link extras written onto a tap [Intent] by [AndroidNotificationPresenter] back into a
 * [NotificationRoute] (the inverse of the presenter's `routeIntent`). Returns `null` when the intent carries
 * no — or an unrecognised — notification route: a normal launcher start, or a [ROUTE_GAME] tap that somehow
 * arrived without its game id.
 */
internal fun Intent.toNotificationRoute(): NotificationRoute? =
    when (getStringExtra(EXTRA_NOTIFICATION_ROUTE)) {
        ROUTE_GAME -> getStringExtra(EXTRA_NOTIFICATION_GAME_ID)?.let { NotificationRoute.Game(it) }
        ROUTE_NOTIFICATIONS -> NotificationRoute.Notifications
        else -> null
    }

/**
 * Where tapping a delivered alert should land the user (#288): an alert referencing exactly one game
 * deep-links to that game's detail; zero or several games open the in-app Notifications screen.
 */
internal fun PendingNotificationAlert.toNotificationRoute(): NotificationRoute =
    games.singleOrNull()?.let { NotificationRoute.Game(it.gameId) } ?: NotificationRoute.Notifications
