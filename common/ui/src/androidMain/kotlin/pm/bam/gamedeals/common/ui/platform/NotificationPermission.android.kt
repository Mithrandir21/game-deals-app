package pm.bam.gamedeals.common.ui.platform

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

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
