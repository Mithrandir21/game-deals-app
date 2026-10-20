package pm.bam.gamedeals.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import pm.bam.gamedeals.domain.repositories.notifications.NotificationsRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.error

/** Broadcast action of the waitlist summary's "Mark as read" button ([AndroidNotificationPresenter]). */
internal const val ACTION_MARK_ALL_READ = "pm.bam.gamedeals.action.MARK_ALL_NOTIFICATIONS_READ"

/**
 * Handles the waitlist summary's "Mark as read" button without opening the app: dismisses the summary straight away,
 * then marks every ITAD notification read. A failure is logged and dropped — the alerts simply stay unread in the
 * app. The iOS equivalent is `markAllNotificationsRead` in the iOS shell.
 */
internal class NotificationActionReceiver : BroadcastReceiver(), KoinComponent {

    private val notificationsRepository: NotificationsRepository by inject()
    private val logger: Logger by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_MARK_ALL_READ) return
        NotificationManagerCompat.from(context).cancel(WAITLIST_SUMMARY_ID)
        // goAsync keeps the process alive for the network call (the system allows about 10 seconds).
        val pending = goAsync()
        scope.launch {
            try {
                notificationsRepository.markAllRead()
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                error(logger, t) { "Mark-all-read from a notification action failed." }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
