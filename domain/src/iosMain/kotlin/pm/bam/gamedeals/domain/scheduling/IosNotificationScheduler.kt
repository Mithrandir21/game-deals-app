package pm.bam.gamedeals.domain.scheduling

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.BackgroundTasks.BGAppRefreshTaskRequest
import platform.BackgroundTasks.BGTaskScheduler
import platform.Foundation.NSDate
import platform.Foundation.NSError
import platform.Foundation.dateWithTimeIntervalSinceNow
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.debug
import pm.bam.gamedeals.logging.warn

/** BGTaskScheduler identifier for the periodic poll — must match Info.plist `BGTaskSchedulerPermittedIdentifiers`. */
const val NOTIFICATION_POLL_TASK_ID = "pm.bam.gamedeals.notificationpoll"

// 6h to mirror the Android WorkManager poll; iOS treats this as the *earliest* time and schedules at its discretion.
private const val POLL_INTERVAL_SECONDS = 6.0 * 60.0 * 60.0

/**
 * iOS [NotificationScheduler] backed by `BGTaskScheduler`. [schedule] submits a one-shot `BGAppRefreshTaskRequest`
 * (the launch handler — registered once at app launch — re-arms the next one); [cancel] clears any pending request.
 * The opt-in toggle and the poll handler are the only callers, mirroring the Android scheduler's lifecycle.
 */
internal class IosNotificationScheduler(private val logger: Logger) : NotificationScheduler {

    @OptIn(ExperimentalForeignApi::class)
    override fun schedule() {
        val request = BGAppRefreshTaskRequest(NOTIFICATION_POLL_TASK_ID).apply {
            earliestBeginDate = NSDate.dateWithTimeIntervalSinceNow(POLL_INTERVAL_SECONDS)
        }
        memScoped {
            val errorVar = alloc<ObjCObjectVar<NSError?>>()
            val submitted = BGTaskScheduler.sharedScheduler.submitTaskRequest(request, errorVar.ptr)
            if (submitted) {
                debug(logger) { "Scheduled BGAppRefresh notification poll ($NOTIFICATION_POLL_TASK_ID)." }
            } else {
                warn(logger) { "Failed to submit BGAppRefresh poll (${errorVar.value?.localizedDescription}) — is $NOTIFICATION_POLL_TASK_ID in BGTaskSchedulerPermittedIdentifiers?" }
            }
        }
    }

    override fun cancel() {
        BGTaskScheduler.sharedScheduler.cancelTaskRequestWithIdentifier(NOTIFICATION_POLL_TASK_ID)
        debug(logger) { "Cancelled BGAppRefresh notification poll." }
    }
}
