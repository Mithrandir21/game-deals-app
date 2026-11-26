package pm.bam.gamedeals.domain.scheduling

import pm.bam.gamedeals.domain.repositories.bundlewatch.BundleWatchChecker
import pm.bam.gamedeals.domain.repositories.franchise.FollowedFranchiseChecker
import pm.bam.gamedeals.domain.repositories.notifications.NotificationPresenter
import pm.bam.gamedeals.domain.repositories.notifications.NotificationSync
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleReminder
import pm.bam.gamedeals.logging.featureflags.FeatureFlag
import pm.bam.gamedeals.logging.featureflags.FeatureFlags

/**
 * The shared body of one background notification poll: drive the ITAD [NotificationSync], the
 * client-side [FollowedFranchiseChecker] (followed-franchise deal alerts) and [BundleWatchChecker] (waitlisted games
 * in bundles and giveaways), then hand any genuinely-new
 * alerts to the platform [NotificationPresenter]. Extracted from the Android [NotificationPollWorker] so the
 * present-only-when-there's-something guard is unit-testable (and reusable by the iOS BGTask handler).
 *
 * An ITAD-sync failure propagates so the caller (the worker) can map it to a WorkManager retry; the
 * franchise check is best-effort and never fails the poll (a still-delivered ITAD sync shouldn't be lost
 * because IGDB/ITAD pricing hiccuped). The Winter Sale reminder is aligned first and told about a posted summary;
 * it never throws, so it can't fail the poll either. The remote switches are refreshed first, so the bundle check
 * follows its [FeatureFlag.BundleWatch] switch.
 */
internal suspend fun runNotificationPoll(
    sync: NotificationSync,
    checker: FollowedFranchiseChecker,
    presenter: NotificationPresenter,
    reminder: WinterSaleReminder,
    bundleWatch: BundleWatchChecker,
    featureFlags: FeatureFlags,
) {
    // The poll is also how switches reach a phone whose app isn't opened; never throws.
    featureFlags.awaitRefresh()
    reminder.sync()
    val syncAlerts = sync.syncAndCollectNew()
    val franchiseAlerts = runCatching { checker.collectCrossedAlerts() }.getOrDefault(emptyList())
    // Never throws; signed out it is empty. Switched off, it is skipped, so it carries on from what it had seen.
    val bundleAlerts = if (featureFlags.isEnabled(FeatureFlag.BundleWatch)) bundleWatch.collectNewAlerts() else emptyList()
    val alerts = syncAlerts + franchiseAlerts + bundleAlerts
    if (alerts.isNotEmpty()) {
        presenter.present(alerts)
        reminder.onSummaryPosted()
    }
}
