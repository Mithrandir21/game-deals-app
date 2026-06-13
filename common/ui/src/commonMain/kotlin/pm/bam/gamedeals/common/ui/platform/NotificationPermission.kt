package pm.bam.gamedeals.common.ui.platform

import androidx.compose.runtime.Composable

/**
 * Requests the OS permission needed to post notifications, the platform way: Android 13+
 * `POST_NOTIFICATIONS` runtime permission (auto-granted below API 33) / iOS
 * `UNUserNotificationCenter` authorization. Obtained via [rememberNotificationPermissionRequester] from a
 * composable (the Account hub's background-alerts toggle) so it can hook the platform launcher.
 */
interface NotificationPermissionRequester {
    /** Triggers the permission prompt (or returns the already-decided result); [onResult] runs on the main thread. */
    fun request(onResult: (granted: Boolean) -> Unit)
}

@Composable
expect fun rememberNotificationPermissionRequester(): NotificationPermissionRequester
