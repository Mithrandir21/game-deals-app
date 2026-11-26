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

/**
 * Reflects whether the OS currently allows this app to post notifications — Android 13+ `POST_NOTIFICATIONS`
 * grant (always `true` below API 33) / iOS `UNUserNotificationCenter` authorization. Re-checked on lifecycle
 * resume, so it updates when the user toggles the permission in system settings and returns. Distinct from
 * the opt-in preference: a user can have opted in while the OS permission is off (e.g. revoked later).
 */
@Composable
expect fun rememberNotificationPermissionGranted(): Boolean
