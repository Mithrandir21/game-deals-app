package pm.bam.gamedeals.domain.scheduling

import pm.bam.gamedeals.domain.repositories.notifications.NotificationPresenter
import pm.bam.gamedeals.domain.repositories.notifications.NotificationSync

/**
 * The shared body of one background notification poll: drive [NotificationSync], then hand any genuinely-new
 * alerts to the platform [NotificationPresenter]. Extracted from the Android [NotificationPollWorker] so the
 * present-only-when-there's-something guard is unit-testable (and reusable by the iOS BGTask handler).
 * Failures propagate so the caller (the worker) can map them to a WorkManager retry.
 */
internal suspend fun runNotificationPoll(sync: NotificationSync, presenter: NotificationPresenter) {
    val alerts = sync.syncAndCollectNew()
    if (alerts.isNotEmpty()) presenter.present(alerts)
}
