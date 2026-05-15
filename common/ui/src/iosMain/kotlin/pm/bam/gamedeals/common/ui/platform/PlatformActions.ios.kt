@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package pm.bam.gamedeals.common.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
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
