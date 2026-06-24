package pm.bam.gamedeals.domain.scheduling

import pm.bam.gamedeals.domain.repositories.franchise.FollowedFranchiseChecker
import pm.bam.gamedeals.domain.repositories.notifications.NotificationPresenter
import pm.bam.gamedeals.domain.repositories.notifications.NotificationSync

/**
 * The shared body of one background notification poll: drive the ITAD [NotificationSync] **and** the
 * client-side [FollowedFranchiseChecker] (followed-franchise deal alerts), then hand any genuinely-new
 * alerts to the platform [NotificationPresenter]. Extracted from the Android [NotificationPollWorker] so the
 * present-only-when-there's-something guard is unit-testable (and reusable by the iOS BGTask handler).
 *
 * An ITAD-sync failure propagates so the caller (the worker) can map it to a WorkManager retry; the
 * franchise check is best-effort and never fails the poll (a still-delivered ITAD sync shouldn't be lost
 * because IGDB/ITAD pricing hiccuped).
 */
internal suspend fun runNotificationPoll(
    sync: NotificationSync,
    checker: FollowedFranchiseChecker,
    presenter: NotificationPresenter,
) {
    val syncAlerts = sync.syncAndCollectNew()
    val franchiseAlerts = runCatching { checker.collectCrossedAlerts() }.getOrDefault(emptyList())
    val alerts = syncAlerts + franchiseAlerts
    if (alerts.isNotEmpty()) presenter.present(alerts)
}
