package pm.bam.gamedeals.domain.scheduling

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform
import platform.BackgroundTasks.BGTask
import platform.BackgroundTasks.BGTaskScheduler
import pm.bam.gamedeals.domain.repositories.franchise.FollowedFranchiseChecker
import pm.bam.gamedeals.domain.repositories.notifications.NotificationPresenter
import pm.bam.gamedeals.domain.repositories.notifications.NotificationSettings
import pm.bam.gamedeals.domain.repositories.notifications.NotificationSync
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.debug
import pm.bam.gamedeals.logging.error

/**
 * Registers the `BGTaskScheduler` launch handler for the periodic notification poll. The iOS app delegate must
 * call this once at launch (before launch completes, as iOS requires) — including background launches, where the
 * Compose entry point that normally starts Koin doesn't run, so the delegate starts Koin first.
 *
 * The handler drives the shared [runNotificationPoll] and re-arms the next request (BGAppRefresh is one-shot),
 * gating the re-arm on the still-current opt-in so an opted-out user isn't perpetually re-scheduled.
 */
fun registerNotificationBackgroundPoll() = NotificationBackgroundPoll.register()

private object NotificationBackgroundPoll {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun register() {
        val logger = KoinPlatform.getKoin().get<Logger>()
        val registered = BGTaskScheduler.sharedScheduler.registerForTaskWithIdentifier(NOTIFICATION_POLL_TASK_ID, null) { task ->
            if (task != null) handle(task)
        }
        debug(logger) { "Registered BGAppRefresh handler ($NOTIFICATION_POLL_TASK_ID): $registered" }
    }

    private fun handle(task: BGTask) {
        val koin = KoinPlatform.getKoin()
        val logger = koin.get<Logger>()

        val job = scope.launch {
            // Re-arm only while still opted in — cancel() already cleared the request on opt-out/logout.
            if (koin.get<NotificationSettings>().isEnabled()) koin.get<NotificationScheduler>().schedule()
            runCatching {
                runNotificationPoll(koin.get<NotificationSync>(), koin.get<FollowedFranchiseChecker>(), koin.get<NotificationPresenter>())
            }.onFailure { error(logger, it) { "Background notification poll failed." } }
        }
        task.expirationHandler = { job.cancel() }
        job.invokeOnCompletion { cause -> task.setTaskCompletedWithSuccess(cause == null) }
    }
}
