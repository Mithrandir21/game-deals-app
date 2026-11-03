package pm.bam.gamedeals.domain.scheduling

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleReminderScheduler
import java.util.concurrent.TimeUnit

internal const val WINTER_SALE_REMINDER_WORK = "winter-sale-reminder"

/** One unique delayed job; it needs a connection because it re-checks the event document before posting. */
internal class AndroidWinterSaleReminderScheduler(private val context: Context) : WinterSaleReminderScheduler {

    override fun schedule(delayMillis: Long) {
        val request = OneTimeWorkRequestBuilder<WinterSaleReminderWorker>()
            .setInitialDelay(delayMillis.coerceAtLeast(0L), TimeUnit.MILLISECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(WINTER_SALE_REMINDER_WORK, ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WINTER_SALE_REMINDER_WORK)
    }
}
