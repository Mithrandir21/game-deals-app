package pm.bam.gamedeals.domain.scheduling

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * The periodic background poll (Phase B). Resolves its collaborators from the process-global Koin graph
 * (`KoinComponent.get()`) — Koin is started in `GameDealsApplication.onCreate()` before any worker runs,
 * so no custom `WorkerFactory`/`Configuration.Provider` is needed (the default `androidx.startup`
 * initializer instantiates this worker via its `(Context, WorkerParameters)` constructor).
 */
internal class NotificationPollWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    override suspend fun doWork(): Result = try {
        runNotificationPoll(get(), get())
        Result.success()
    } catch (ce: CancellationException) {
        throw ce
    } catch (_: Throwable) {
        Result.retry() // transient (network/auth) — let WorkManager back off and retry.
    }
}
