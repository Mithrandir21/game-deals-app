package pm.bam.gamedeals.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import pm.bam.gamedeals.MainActivity
import pm.bam.gamedeals.R
import pm.bam.gamedeals.domain.repositories.notifications.NotificationPresenter
import pm.bam.gamedeals.domain.repositories.notifications.PendingNotificationAlert

internal const val EXTRA_NOTIFICATION_ROUTE = "extra_notification_route"
internal const val EXTRA_NOTIFICATION_GAME_ID = "extra_notification_game_id"
internal const val ROUTE_GAME = "game"
internal const val ROUTE_NOTIFICATIONS = "notifications"

/**
 * Android [NotificationPresenter] (Phase B). Posts one tray notification per alert, grouped under a
 * summary so multiple collapse (Android 7+). The tap [PendingIntent] re-enters [MainActivity] with route
 * extras: an alert referencing exactly one game deep-links to its detail, anything else opens the in-app
 * Notifications screen (#288 behaviour). Lives in `:app` because it must target [MainActivity].
 */
internal class AndroidNotificationPresenter(private val context: Context) : NotificationPresenter {

    private val manager = NotificationManagerCompat.from(context)

    init {
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.notification_channel_waitlist))
                .build()
        )
    }

    override suspend fun present(alerts: List<PendingNotificationAlert>) {
        if (alerts.isEmpty() || !manager.areNotificationsEnabled()) return
        try {
            alerts.forEach { alert ->
                manager.notify(
                    alert.notificationId.hashCode(),
                    NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(alert.title)
                        .setAutoCancel(true)
                        .setGroup(GROUP_KEY)
                        .setContentIntent(tapIntent(alert))
                        .build(),
                )
            }
            if (alerts.size > 1) {
                manager.notify(
                    SUMMARY_ID,
                    NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setGroup(GROUP_KEY)
                        .setGroupSummary(true)
                        .setContentIntent(routeIntent(ROUTE_NOTIFICATIONS, gameId = null, requestCode = SUMMARY_ID))
                        .build(),
                )
            }
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS revoked between the check and notify() — drop silently.
        }
    }

    private fun tapIntent(alert: PendingNotificationAlert): PendingIntent {
        val singleGame = alert.games.singleOrNull()
        return if (singleGame != null) {
            routeIntent(ROUTE_GAME, gameId = singleGame.gameId, requestCode = alert.notificationId.hashCode())
        } else {
            routeIntent(ROUTE_NOTIFICATIONS, gameId = null, requestCode = alert.notificationId.hashCode())
        }
    }

    private fun routeIntent(route: String, gameId: String?, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(EXTRA_NOTIFICATION_ROUTE, route)
            gameId?.let { putExtra(EXTRA_NOTIFICATION_GAME_ID, it) }
        }
        return PendingIntent.getActivity(context, requestCode, intent, PENDING_FLAGS)
    }

    private companion object {
        const val CHANNEL_ID = "itad_waitlist"
        const val GROUP_KEY = "itad_waitlist_group"
        const val SUMMARY_ID = 0
        const val PENDING_FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    }
}
