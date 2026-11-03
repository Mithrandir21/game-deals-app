package pm.bam.gamedeals.notifications

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import pm.bam.gamedeals.MainActivity
import pm.bam.gamedeals.R
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleReminderPresenter

internal const val ROUTE_WINTER_SALE_GUIDE = "winter_sale_guide"
private const val WINTER_SALE_REMINDER_ID = Int.MIN_VALUE + 2

/** Posts the single Winter Sale reminder; tapping it opens the guide, or Home if the event is off by then. */
internal class AndroidWinterSaleReminderPresenter(private val context: Context) : WinterSaleReminderPresenter {

    private val manager = NotificationManagerCompat.from(context)

    // POST_NOTIFICATIONS: bails on !areNotificationsEnabled() and catches a revoke between the check and notify().
    @SuppressLint("MissingPermission")
    override suspend fun present() {
        if (!manager.areNotificationsEnabled()) return
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.notification_channel_sale_reminder))
                .build()
        )
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra(EXTRA_NOTIFICATION_ROUTE, ROUTE_WINTER_SALE_GUIDE)
        }
        val contentIntent = PendingIntent.getActivity(context, WINTER_SALE_REMINDER_ID, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.notification_sale_reminder_title))
            .setContentText(context.getString(R.string.notification_sale_reminder_text))
            .setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(R.string.notification_sale_reminder_text)))
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        try {
            manager.notify(WINTER_SALE_REMINDER_ID, notification)
        } catch (_: SecurityException) {
            Unit
        }
    }

    private companion object {
        const val CHANNEL_ID = "sale_reminders"
    }
}
