package pm.bam.gamedeals.domain.scheduling

import pm.bam.gamedeals.domain.repositories.notifications.NotificationPresenter
import pm.bam.gamedeals.domain.repositories.notifications.NotificationSync
import pm.bam.gamedeals.domain.repositories.pricewatch.PriceWatchChecker

/**
 * The shared body of one background notification poll: drive the ITAD [NotificationSync] **and** the
 * client-side [PriceWatchChecker] (target-price alerts — Phase 3), then hand any genuinely-new alerts to
 * the platform [NotificationPresenter]. Extracted from the Android [NotificationPollWorker] so the
 * present-only-when-there's-something guard is unit-testable (and reusable by the iOS BGTask handler).
 * An ITAD-sync failure propagates so the caller (the worker) can map it to a WorkManager retry; the
 * price-watch check is best-effort inside the checker and never fails the poll.
 */
internal suspend fun runNotificationPoll(
    sync: NotificationSync,
    priceWatchChecker: PriceWatchChecker,
    presenter: NotificationPresenter,
) {
    val alerts = sync.syncAndCollectNew() + priceWatchChecker.collectCrossedAlerts()
    if (alerts.isNotEmpty()) presenter.present(alerts)
}
