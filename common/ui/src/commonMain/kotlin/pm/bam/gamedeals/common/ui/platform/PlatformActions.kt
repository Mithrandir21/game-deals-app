package pm.bam.gamedeals.common.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * One-shot, platform-native side effects the UI can request without knowing how
 * each platform implements them. Bound at the host root via
 * [LocalPlatformActions] and consumed by composables and by collectors of
 * ViewModel event flows.
 */
interface PlatformActions {
    fun share(text: String)
}

val LocalPlatformActions = staticCompositionLocalOf<PlatformActions> {
    error("PlatformActions not provided. Wrap your content in CompositionLocalProvider(LocalPlatformActions provides …).")
}

@Composable
expect fun rememberPlatformActions(): PlatformActions
