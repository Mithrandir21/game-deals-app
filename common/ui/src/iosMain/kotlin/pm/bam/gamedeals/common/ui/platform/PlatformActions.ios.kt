@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package pm.bam.gamedeals.common.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSURL
import platform.SafariServices.SFSafariViewController
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController

class IosPlatformActions : PlatformActions {
    override fun share(text: String) {
        val keyWindow = resolveKeyWindow() ?: return
        val rootViewController = keyWindow.rootViewController ?: return

        val vc = UIActivityViewController(
            activityItems = listOf(text),
            applicationActivities = null,
        )

        // iPad requires a popover anchor; without it UIKit throws NSInvalidArgumentException.
        // Anchor a small rect near the center of the resolved key window so the popover is
        // centered regardless of where the user tapped.
        vc.popoverPresentationController?.let { popover ->
            popover.sourceView = keyWindow
            keyWindow.bounds.useContents {
                popover.sourceRect = CGRectMake(
                    x = size.width / 2.0 - 1.0,
                    y = size.height / 2.0 - 1.0,
                    width = 2.0,
                    height = 2.0,
                )
            }
        }

        rootViewController.presentViewController(vc, animated = true, completion = null)
    }

    override fun openInApp(url: String) {
        // SFSafariViewController only accepts http/https URLs (all of ours are https). A malformed URL
        // or a missing host window are the only ways this no-ops.
        val nsUrl = NSURL.URLWithString(url) ?: return
        val rootViewController = resolveKeyWindow()?.rootViewController ?: return
        val safari = SFSafariViewController(uRL = nsUrl)
        rootViewController.presentViewController(safari, animated = true, completion = null)
    }

    override fun openAppNotificationSettings() {
        // iOS exposes only the app's Settings root (no direct notifications subpage pre-iOS 16); the
        // Notifications section is one tap in. The string constant is non-null at runtime.
        val nsUrl = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(nsUrl, options = emptyMap<Any?, Any>(), completionHandler = null)
    }

    /**
     * Resolves the app's current key window via `connectedScenes`. `UIApplication.keyWindow`
     * is deprecated since iOS 13 and returns nil for multi-scene apps. Prefers a foreground-active
     * scene's `keyWindow`, falling back to that scene's first window, then to any scene's window.
     */
    private fun resolveKeyWindow(): UIWindow? {
        val scenes = UIApplication.sharedApplication.connectedScenes
        var fallbackKeyWindow: UIWindow? = null
        var fallbackFirstWindow: UIWindow? = null

        for (scene in scenes) {
            val windowScene = scene as? UIWindowScene ?: continue
            val windows = windowScene.windows.filterIsInstance<UIWindow>()
            val sceneKeyWindow = windowScene.keyWindow ?: windows.firstOrNull()

            if (windowScene.activationState == UISceneActivationStateForegroundActive) {
                if (sceneKeyWindow != null) return sceneKeyWindow
            } else {
                if (fallbackKeyWindow == null && windowScene.keyWindow != null) {
                    fallbackKeyWindow = windowScene.keyWindow
                }
                if (fallbackFirstWindow == null && windows.isNotEmpty()) {
                    fallbackFirstWindow = windows.first()
                }
            }
        }
        return fallbackKeyWindow ?: fallbackFirstWindow
    }
}

@Composable
actual fun rememberPlatformActions(): PlatformActions =
    remember { IosPlatformActions() }
