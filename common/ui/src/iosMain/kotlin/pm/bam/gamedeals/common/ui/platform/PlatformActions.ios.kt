package pm.bam.gamedeals.common.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

class IosPlatformActions : PlatformActions {
    override fun share(text: String) {
        val vc = UIActivityViewController(
            activityItems = listOf(text),
            applicationActivities = null,
        )
        UIApplication.sharedApplication.keyWindow
            ?.rootViewController
            ?.presentViewController(vc, animated = true, completion = null)
    }
}

@Composable
actual fun rememberPlatformActions(): PlatformActions =
    remember { IosPlatformActions() }
