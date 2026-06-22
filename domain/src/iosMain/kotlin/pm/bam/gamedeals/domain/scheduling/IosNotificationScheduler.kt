package pm.bam.gamedeals.domain.scheduling

import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.debug

/**
 * iOS [NotificationScheduler]. The periodic `BGTaskScheduler` poll is not wired yet, so this is a logged
 * no-op: the in-app opt-in toggle still persists via `NotificationSettings`, but no background work is
 * registered. It exists so Koin can resolve `NotificationScheduler` on iOS (the Account hub's
 * `NotificationSettingsViewModel` depends on it) — without it the Account tab crashes on compose.
 */
internal class IosNotificationScheduler(private val logger: Logger) : NotificationScheduler {

    override fun schedule() {
        debug(logger) { "NotificationScheduler.schedule() called on iOS — BGTaskScheduler not yet wired, no background poll registered." }
    }

    override fun cancel() {
        debug(logger) { "NotificationScheduler.cancel() called on iOS — no background poll to tear down." }
    }
}
