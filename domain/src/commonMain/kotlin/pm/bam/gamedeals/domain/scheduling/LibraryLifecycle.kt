package pm.bam.gamedeals.domain.scheduling

import pm.bam.gamedeals.domain.models.AuthState
import pm.bam.gamedeals.domain.repositories.collection.CollectionRepository
import pm.bam.gamedeals.domain.repositories.ignored.IgnoredRepository
import pm.bam.gamedeals.domain.repositories.waitlist.WaitlistRepository
import pm.bam.gamedeals.logging.LogLevel
import pm.bam.gamedeals.logging.Logger

/**
 * Reconciles the user's ITAD library (waitlist / collection / ignored) with the current auth state, owned at
 * app scope rather than by any one screen. On [AuthState.LoggedIn] it runs the remote-as-truth reconcile so the
 * Room-backed id sets — which every badge, the Home stat cards, and the library lists read reactively — are
 * populated regardless of which login entry point was used (the Account tab, the global sign-in sheet, or
 * onboarding) or which tab is alive. On [AuthState.LoggedOut] it wipes those rows so a different account that
 * logs in next starts clean instead of briefly seeing the previous user's set. Each step is best-effort: a
 * failure is logged and the others still run. Shared so both platforms reconcile identically from their own
 * auth-state observer (mirrors [applyNotificationLifecycle]).
 */
suspend fun applyLibraryLifecycle(
    state: AuthState,
    waitlist: WaitlistRepository,
    collection: CollectionRepository,
    ignored: IgnoredRepository,
    logger: Logger,
) = when (state) {
    is AuthState.LoggedIn -> {
        runCatching { waitlist.getWaitlist() }.onFailure { logger.log(LogLevel.ERROR, tag = "LibraryLifecycle", throwable = it) { "Waitlist sync failed" } }
        runCatching { collection.getCollection() }.onFailure { logger.log(LogLevel.ERROR, tag = "LibraryLifecycle", throwable = it) { "Collection sync failed" } }
        runCatching { ignored.getIgnored() }.onFailure { logger.log(LogLevel.ERROR, tag = "LibraryLifecycle", throwable = it) { "Ignored sync failed" } }
        Unit
    }
    AuthState.LoggedOut -> {
        runCatching { waitlist.clearLocal() }.onFailure { logger.log(LogLevel.ERROR, tag = "LibraryLifecycle", throwable = it) { "Waitlist clear-local failed" } }
        runCatching { collection.clearLocal() }.onFailure { logger.log(LogLevel.ERROR, tag = "LibraryLifecycle", throwable = it) { "Collection clear-local failed" } }
        runCatching { ignored.clearLocal() }.onFailure { logger.log(LogLevel.ERROR, tag = "LibraryLifecycle", throwable = it) { "Ignored clear-local failed" } }
        Unit
    }
}
