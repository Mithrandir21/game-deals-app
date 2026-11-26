package pm.bam.gamedeals.domain.repositories.notifications

/**
 * Platform port that posts [PendingNotificationAlert]s to the OS notification tray — Android
 * `NotificationManagerCompat` / iOS `UNUserNotificationCenter`. The concrete implementations live in the
 * app hosts (`:app` / `:iosApp`) because the tap action must deep-link into the host's navigation; the
 * background driver ([NotificationSync] caller) only depends on this interface.
 */
interface NotificationPresenter {
    suspend fun present(alerts: List<PendingNotificationAlert>)
}
