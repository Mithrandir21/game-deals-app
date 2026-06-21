package pm.bam.gamedeals.common.ui.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
actual fun rememberNotificationPermissionRequester(): NotificationPermissionRequester {
    // Holds the in-flight callback while the system permission dialog is up.
    val pending = remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending.value?.invoke(granted)
        pending.value = null
    }
    return remember(launcher) {
        object : NotificationPermissionRequester {
            override fun request(onResult: (Boolean) -> Unit) {
                // POST_NOTIFICATIONS is only a runtime permission on API 33+; below that, posting is allowed.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                    onResult(true)
                    return
                }
                pending.value = onResult
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@Composable
actual fun rememberNotificationPermissionGranted(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(isPostNotificationsGranted(context)) }
    // Re-read on resume so a grant/revoke made in system settings (or via the prompt, which pauses the
    // host activity) is reflected when the user returns.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = isPostNotificationsGranted(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return granted
}

private fun isPostNotificationsGranted(context: Context): Boolean =
    // POST_NOTIFICATIONS is only a runtime permission on API 33+; below that, posting is always allowed.
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
