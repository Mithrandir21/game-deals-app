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
internal const val ROUTE_NOTIFICATIONS = "notifications"
internal const val ROUTE_FOLLOWED_SERIES = "followed_series"

/** Cap the per-game lines listed in an expanded notification so the tray text stays readable. */
private const val MAX_GAME_LINES = 6

/**
 * Android [NotificationPresenter] (#7 notification revamp). Posts **at most two** tray notifications per
 * poll — one bundled summary per path — instead of one per alert:
 *  - a **waitlist** summary listing the newly-dropped waitlisted games (tap → Notifications list), and
 *  - a **followed-franchise** summary listing the newly-on-sale franchise games (tap → Followed-series).
 *
 * This is the de-spam fix: the franchise path used to fan out one tray notification per on-sale game.
 * Lives in `:app` because the tap [PendingIntent] must target [MainActivity].
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
        // A followed-franchise alert carries a gameId (and no games); an ITAD waitlist alert carries games.
        val (franchiseAlerts, waitlistAlerts) = alerts.partition { it.gameId != null }
        try {
            if (waitlistAlerts.isNotEmpty()) {
                notifySummary(
                    id = WAITLIST_SUMMARY_ID,
                    title = context.getString(R.string.notification_waitlist_summary_title),
                    lines = waitlistAlerts.flatMap { it.games }.map { it.toLine() },
                    fallbackText = context.getString(R.string.notification_summary_text, waitlistAlerts.size),
                    contentIntent = routeIntent(ROUTE_NOTIFICATIONS, WAITLIST_SUMMARY_ID),
                )
            }
            if (franchiseAlerts.isNotEmpty()) {
                notifySummary(
                    id = FRANCHISE_SUMMARY_ID,
                    title = context.getString(R.string.notification_franchise_summary_title),
                    // Each franchise alert's title already self-describes ("X is Y% off in Z").
                    lines = franchiseAlerts.map { it.title },
                    fallbackText = context.getString(R.string.notification_summary_text, franchiseAlerts.size),
                    contentIntent = routeIntent(ROUTE_FOLLOWED_SERIES, FRANCHISE_SUMMARY_ID),
                )
            }
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS revoked between the check and notify() — drop silently.
        }
    }

    private fun notifySummary(
        id: Int,
        title: String,
        lines: List<String>,
        fallbackText: String,
        contentIntent: PendingIntent,
    ) {
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
        when {
            lines.isEmpty() -> builder.setContentText(fallbackText)
            lines.size == 1 -> builder.setContentText(lines.first())
            else -> {
                builder.setContentText(lines.first())
                val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
                lines.take(MAX_GAME_LINES).forEach { style.addLine(it) }
                if (lines.size > MAX_GAME_LINES) {
                    style.setSummaryText(context.getString(R.string.notification_more_games, lines.size - MAX_GAME_LINES))
                }
                builder.setStyle(style)
            }
        }
        manager.notify(id, builder.build())
    }

    private fun NotificationDealGame.toLine(): String {
        val best = bestDeal
        return if (best != null) {
            context.getString(R.string.notification_game_line, title, best.cutPercent, best.salePriceDenominated)
        } else {
            context.getString(R.string.notification_game_line_expired, title)
        }
    }

    private fun routeIntent(route: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(EXTRA_NOTIFICATION_ROUTE, route)
        }
        return PendingIntent.getActivity(context, requestCode, intent, PENDING_FLAGS)
    }

    private companion object {
        const val CHANNEL_ID = "itad_waitlist"
        const val WAITLIST_SUMMARY_ID = Int.MIN_VALUE
        const val FRANCHISE_SUMMARY_ID = Int.MIN_VALUE + 1
        const val PENDING_FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    }
}
