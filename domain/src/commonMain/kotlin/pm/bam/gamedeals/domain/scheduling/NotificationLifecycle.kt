package pm.bam.gamedeals.domain.scheduling

import pm.bam.gamedeals.domain.models.AuthState
import pm.bam.gamedeals.domain.repositories.notifications.NotificationSettings
import pm.bam.gamedeals.domain.repositories.notifications.SurfacedNotificationStore

/**
 * Reconciles the background notification poll with the current auth state + opt-in. The poll is armed purely by
 * the opt-in, **independent of login**: it also serves followed-franchise deal alerts, which need no ITAD account,
 * so it runs logged-out too (the ITAD sync inside the poll is separately login-gated). The schedule is idempotent,
 * so this also covers app start / reboot. On logout the surfaced-id set is cleared so a different account re-alerts
 * cleanly — the opt-in preference (and the independent followed-franchise dedupe store) are preserved. Shared so
 * both platforms reconcile identically from their own auth-state observer.
 */
suspend fun applyNotificationLifecycle(
    state: AuthState,
    settings: NotificationSettings,
    scheduler: NotificationScheduler,
    surfacedStore: SurfacedNotificationStore,
) {
    if (settings.isEnabled()) scheduler.schedule() else scheduler.cancel()
    if (state is AuthState.LoggedOut) surfacedStore.replace(emptySet())
}
