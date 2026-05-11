package pm.bam.gamedeals.common.ui.share

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication

@Composable
actual fun rememberShareDealAction(): (text: String) -> Unit {
    return remember {
        { text ->
            val vc = UIActivityViewController(
                activityItems = listOf(text),
                applicationActivities = null,
            )
            UIApplication.sharedApplication.keyWindow
                ?.rootViewController
                ?.presentViewController(vc, animated = true, completion = null)
        }
    }
}
