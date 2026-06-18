package pm.bam.gamedeals.notifications

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import pm.bam.gamedeals.MainActivity
import pm.bam.gamedeals.R
import pm.bam.gamedeals.domain.models.NotificationDealGame
import pm.bam.gamedeals.domain.repositories.notifications.NotificationPresenter
import pm.bam.gamedeals.domain.repositories.notifications.PendingNotificationAlert

internal const val EXTRA_NOTIFICATION_ROUTE = "extra_notification_route"
internal const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
internal const val ROUTE_NOTIFICATION_DETAIL = "notification_detail"
internal const val ROUTE_NOTIFICATIONS = "notifications"

/** Cap the per-game lines listed in an expanded notification so the tray text stays readable. */
private const val MAX_GAME_LINES = 6

/**
 * Android [NotificationPresenter] (Phase B; rich text — #272 follow-up). Posts one tray notification per
 * alert, grouped under a summary so multiple collapse (Android 7+). Each notification lists its games and
 * their best deals (InboxStyle); the tap [PendingIntent] re-enters [MainActivity] and deep-links to that
 * notification's in-app detail screen, while the group summary opens the Notifications list. Lives in
 * `:app` because it must target [MainActivity].
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
                val lines = alert.games.map { it.toLine() }
                val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(alert.title)
                    .setAutoCancel(true)
                    .setGroup(GROUP_KEY)
                    .setContentIntent(tapIntent(alert))
                when {
                    lines.isEmpty() -> Unit
                    lines.size == 1 -> builder.setContentText(lines.first())
                    else -> {
                        builder.setContentText(lines.first())
                        val style = NotificationCompat.InboxStyle().setBigContentTitle(alert.title)
                        lines.take(MAX_GAME_LINES).forEach { style.addLine(it) }
                        if (lines.size > MAX_GAME_LINES) {
                            style.setSummaryText(context.getString(R.string.notification_more_games, lines.size - MAX_GAME_LINES))
                        }
                        builder.setStyle(style)
                    }
                }
                manager.notify(alert.notificationId.hashCode(), builder.build())
            }
            if (alerts.size > 1) {
                val summaryTitle = context.getString(R.string.notification_channel_waitlist)
                val summaryText = context.getString(R.string.notification_summary_text, alerts.size)
                manager.notify(
                    SUMMARY_ID,
                    NotificationCompat.Builder(context, CHANNEL_ID)
                        .setSmallIcon(R.mipmap.ic_launcher)
                        .setContentTitle(summaryTitle)
                        .setContentText(summaryText)
                        .setStyle(
                            NotificationCompat.InboxStyle()
                                .setSummaryText(summaryText)
                        )
                        .setGroup(GROUP_KEY)
                        .setGroupSummary(true)
                        .setContentIntent(routeIntent(ROUTE_NOTIFICATIONS, notificationId = null, requestCode = SUMMARY_ID))
                        .build(),
                )
            }
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS revoked between the check and notify() — drop silently.
        }
    }

    private fun NotificationDealGame.toLine(): String {
        val best = bestDeal
        return if (best != null) {
            context.getString(R.string.notification_game_line, title, best.cutPercent, best.salePriceDenominated)
        } else {
            context.getString(R.string.notification_game_line_expired, title)
        }
    }

    private fun tapIntent(alert: PendingNotificationAlert): PendingIntent {
        val requestCode = alert.notificationId.hashCode()
        return when (val route = alert.toNotificationRoute()) {
            is NotificationRoute.NotificationDetail ->
                routeIntent(ROUTE_NOTIFICATION_DETAIL, notificationId = route.notificationId, requestCode = requestCode)
            NotificationRoute.Notifications -> routeIntent(ROUTE_NOTIFICATIONS, notificationId = null, requestCode = requestCode)
        }
    }

    private fun routeIntent(route: String, notificationId: String?, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(EXTRA_NOTIFICATION_ROUTE, route)
            notificationId?.let { putExtra(EXTRA_NOTIFICATION_ID, it) }
        }
        return PendingIntent.getActivity(context, requestCode, intent, PENDING_FLAGS)
    }

    private companion object {
        const val CHANNEL_ID = "itad_waitlist"
        const val GROUP_KEY = "itad_waitlist_group"
        const val SUMMARY_ID = Int.MIN_VALUE
        const val PENDING_FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    }
}
