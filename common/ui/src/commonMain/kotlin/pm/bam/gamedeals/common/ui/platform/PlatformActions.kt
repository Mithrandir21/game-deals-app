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

/**
 * No-op fallback so `@Preview` composables that read [LocalPlatformActions] don't crash
 * before a real binding is installed at the host (MainActivity / iOS entry).
 */
object NoOpPlatformActions : PlatformActions {
    override fun share(text: String) = Unit
}

val LocalPlatformActions = staticCompositionLocalOf<PlatformActions> { NoOpPlatformActions }

@Composable
expect fun rememberPlatformActions(): PlatformActions
