package pm.bam.gamedeals.common.ui.platform

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class AndroidPlatformActions(private val context: Context) : PlatformActions {
    override fun share(text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, null))
    }

    override suspend fun shareImage(png: ByteArray, text: String) {
        val uri = try {
            withContext(Dispatchers.IO) { writeShareImage(png) }
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null // the app hasn't declared the FileProvider for SHARE_IMAGE_AUTHORITY_SUFFIX
        }
        if (uri == null) return share(text)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, text)
            // ClipData carries the read grant through the chooser to whichever app is picked.
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            context.startActivity(Intent.createChooser(send, null))
        } catch (_: ActivityNotFoundException) {
            share(text)
        }
    }

    /**
     * Writes [png] into the cache folder the app's FileProvider exposes and returns its content URI. Earlier share
     * images are removed first: nothing is kept once a newer one is shared.
     */
    private fun writeShareImage(png: ByteArray): Uri {
        val dir = File(context.cacheDir, SHARE_IMAGE_DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "game-deals-${System.currentTimeMillis()}.png").apply { writeBytes(png) }
        return FileProvider.getUriForFile(context, context.packageName + SHARE_IMAGE_AUTHORITY_SUFFIX, file)
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

    override fun openStoreListing(storeId: String) {
        if (storeId.isEmpty()) return
        // market:// hands straight to the Play client. It is absent on devices without Play services (and on
        // most emulators), so fall back to the web listing, which any browser can take.
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$storeId")))
        } catch (_: ActivityNotFoundException) {
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$storeId"))
                )
            } catch (_: ActivityNotFoundException) {
                // No Play client and no browser — give up silently rather than crash.
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

/**
 * The app module declares a FileProvider with authority `<applicationId>` + this suffix, exposing [SHARE_IMAGE_DIR]
 * under the cache dir (`app/src/main/res/xml/share_images.xml`).
 */
const val SHARE_IMAGE_AUTHORITY_SUFFIX = ".shareimages"
private const val SHARE_IMAGE_DIR = "shared"

@Composable
actual fun rememberPlatformActions(): PlatformActions {
    val context = LocalContext.current
    return remember(context) { AndroidPlatformActions(context) }
}
