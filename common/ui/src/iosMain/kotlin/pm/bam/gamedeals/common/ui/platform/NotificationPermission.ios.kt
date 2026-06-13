package pm.bam.gamedeals.common.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

@Composable
actual fun rememberNotificationPermissionRequester(): NotificationPermissionRequester =
    remember {
        object : NotificationPermissionRequester {
            override fun request(onResult: (Boolean) -> Unit) {
                val options = UNAuthorizationOptionAlert or UNAuthorizationOptionBadge or UNAuthorizationOptionSound
                UNUserNotificationCenter.currentNotificationCenter()
                    .requestAuthorizationWithOptions(options) { granted, _ ->
                        // The completion runs off the main thread; hop back before invoking the UI callback.
                        dispatch_async(dispatch_get_main_queue()) { onResult(granted) }
                    }
            }
        }
    }
