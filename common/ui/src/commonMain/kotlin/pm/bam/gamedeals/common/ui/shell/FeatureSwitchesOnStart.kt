package pm.bam.gamedeals.common.ui.shell

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.LifecycleStartEffect
import org.koin.compose.koinInject
import pm.bam.gamedeals.logging.featureflags.FeatureFlags

/**
 * Re-reads the remote feature switches each time the app comes to the front, so a switch flipped while the app sat in
 * the background takes effect on return. The reader skips the fetch when it fetched in the last 15 minutes. Placed
 * once, at the root of the app's content (Android's `MainActivity`, iOS's `App`).
 */
@Composable
fun RefreshFeatureSwitchesOnStart(featureFlags: FeatureFlags = koinInject()) {
    LifecycleStartEffect(featureFlags) {
        featureFlags.refresh()
        onStopOrDispose { }
    }
}
