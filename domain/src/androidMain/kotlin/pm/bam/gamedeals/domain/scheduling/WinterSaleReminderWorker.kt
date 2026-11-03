package pm.bam.gamedeals.domain.scheduling

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleReminder
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleReminderDecision
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleReminderPresenter

/** Fires the sale reminder only if the event is still on and alerts are still enabled when it comes due. */
internal class WinterSaleReminderWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    override suspend fun doWork(): Result = when (get<WinterSaleReminder>().claimDelivery()) {
        WinterSaleReminderDecision.Show -> {
            get<WinterSaleReminderPresenter>().present()
            Result.success()
        }
        WinterSaleReminderDecision.Retry -> if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.success()
        WinterSaleReminderDecision.Skip -> Result.success()
    }

    private companion object {
        const val MAX_RETRIES = 3
    }
}
