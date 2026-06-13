package pm.bam.gamedeals.domain.scheduling

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

internal const val NOTIFICATION_POLL_WORK = "itad-notification-poll"

/**
 * Android [NotificationScheduler] — a unique [WorkManager] periodic poll. The interval is **6h**:
 * WorkManager's floor is 15 min and Doze coalesces/defers background work anyway (timing is best-effort,
 * not guaranteed), so a longer interval is fine for price-drop alerts and avoids needless OAuth-refresh
 * churn. [NetworkType.CONNECTED] skips the run when offline; [ExistingPeriodicWorkPolicy.KEEP] makes
 * [schedule] idempotent so it's safe to call on every app start to re-arm after reboot/update.
 */
internal class AndroidNotificationScheduler(private val context: Context) : NotificationScheduler {

    override fun schedule() {
        val request = PeriodicWorkRequestBuilder<NotificationPollWorker>(POLL_INTERVAL_HOURS, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            NOTIFICATION_POLL_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(NOTIFICATION_POLL_WORK)
    }

    private companion object {
        const val POLL_INTERVAL_HOURS = 6L
    }
}
