package pm.bam.gamedeals.notifications

import pm.bam.gamedeals.domain.models.AuthState
import pm.bam.gamedeals.domain.repositories.notifications.NotificationSettings
import pm.bam.gamedeals.domain.repositories.notifications.SurfacedNotificationStore
import pm.bam.gamedeals.domain.scheduling.NotificationScheduler

/**
 * Reconciles the background notification poll with the current auth state + opt-in (Phase D). On login the
 * poll is (re-)armed only when the user has opted in (the schedule is idempotent, so this also covers app
 * start / reboot); on logout it's cancelled and the surfaced-id set cleared so a different account re-alerts
 * cleanly — the opt-in preference itself is preserved. Extracted from `GameDealsApplication` so the
 * per-state behaviour is unit-testable; the Application owns the auth-state collection and error handling.
 */
internal suspend fun applyNotificationLifecycle(
    state: AuthState,
    settings: NotificationSettings,
    scheduler: NotificationScheduler,
    surfacedStore: SurfacedNotificationStore,
) {
    when (state) {
        is AuthState.LoggedIn -> if (settings.isEnabled()) scheduler.schedule()
        AuthState.LoggedOut -> {
            scheduler.cancel()
            surfacedStore.replace(emptySet())
        }
    }
}
