package pm.bam.gamedeals.common.ui.platform

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

class AndroidPlatformActions(private val context: Context) : PlatformActions {
    override fun share(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, null))
    }

    override fun openInApp(url: String) {
        val uri = Uri.parse(url)
        try {
            // A Custom Tab degrades to a plain browser tab automatically when the default browser
            // doesn't support the protocol; this catch only handles the no-browser-at-all case.
            CustomTabsIntent.Builder().build().launchUrl(context, uri)
        } catch (_: ActivityNotFoundException) {
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (_: ActivityNotFoundException) {
                // Nothing on the device can open a URL — give up silently rather than crash.
            }
        }
    }

    override fun openAppNotificationSettings() {
        // The per-app notification settings screen (API 26+, which is our minSdk).
        val notificationSettings = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        try {
            context.startActivity(notificationSettings)
        } catch (_: ActivityNotFoundException) {
            // Fall back to the app's details page, which always has a Notifications entry.
            try {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    )
                )
            } catch (_: ActivityNotFoundException) {
                // No settings activity available — give up silently rather than crash.
            }
        }
    }
}

@Composable
actual fun rememberPlatformActions(): PlatformActions {
    val context = LocalContext.current
    return remember(context) { AndroidPlatformActions(context) }
}
