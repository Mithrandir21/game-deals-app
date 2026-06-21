package pm.bam.gamedeals.common.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusProvisional
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

@Composable
actual fun rememberNotificationPermissionGranted(): Boolean {
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(false) }
    // Re-query authorization on resume so a change made in the iOS Settings app is reflected on return.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                UNUserNotificationCenter.currentNotificationCenter()
                    .getNotificationSettingsWithCompletionHandler { settings ->
                        val status = settings?.authorizationStatus
                        val authorized = status == UNAuthorizationStatusAuthorized || status == UNAuthorizationStatusProvisional
                        dispatch_async(dispatch_get_main_queue()) { granted = authorized }
                    }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return granted
}
