package pm.bam.gamedeals.domain.scheduling

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleReminderScheduler
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

internal const val WINTER_SALE_REMINDER_WORK = "winter-sale-reminder"

/**
 * One unique delayed job; it needs a connection because it re-checks the event document before posting. If the document
 * host can't be reached, it retries with a growing wait (2, 4, 8… minutes), so a short outage doesn't lose the reminder.
 */
internal class AndroidWinterSaleReminderScheduler(private val context: Context) : WinterSaleReminderScheduler {

    override fun schedule(delayMillis: Long) {
        val request = OneTimeWorkRequestBuilder<WinterSaleReminderWorker>()
            .setInitialDelay(delayMillis.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, RETRY_BACKOFF_MINUTES, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WINTER_SALE_REMINDER_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WINTER_SALE_REMINDER_WORK)
    }

    override suspend fun isPending(): Boolean =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(WINTER_SALE_REMINDER_WORK).first().any { !it.state.isFinished }

    private companion object {
        const val RETRY_BACKOFF_MINUTES = 2L
    }
}
