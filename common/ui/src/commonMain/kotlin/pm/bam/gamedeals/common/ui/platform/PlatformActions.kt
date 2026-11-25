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

    /**
     * Shares a PNG image (a deal card, a year card) together with [text] through the system share sheet, so chat apps
     * get the picture plus the link. [png] comes from [pm.bam.gamedeals.common.ui.share.ShareCardCapture]. Suspends
     * while the image is written somewhere the receiving app can read it. Falls back to sharing [text] alone.
     */
    suspend fun shareImage(png: ByteArray, text: String) = share(text)

    /**
     * Opens [url] in an in-app browser tab — a Chrome Custom Tab on Android, an
     * `SFSafariViewController` on iOS — so the user stays in-context (and signed in) rather than being
     * sent to the standalone system browser. Implementations fall back gracefully (e.g. to the system
     * browser) if no in-app tab provider is available.
     */
    fun openInApp(url: String)

    /**
     * Opens this app's OS-level notification settings — the Android per-app notification screen / the iOS
     * Settings page for the app — so a user who has blocked notifications can re-enable them. Used when the
     * in-app permission prompt can no longer be shown (already denied). Falls back to the app details
     * screen on Android if the notification screen isn't resolvable; no-ops if nothing can handle it.
     */
    fun openAppNotificationSettings()

    /**
     * Opens this app's own listing in the platform store — Google Play on Android, the App Store on iOS — so
     * a user told they are below the minimum supported version can actually update.
     *
     * Deliberately *not* routed through [openInApp]: a store listing has to leave the app and reach the native
     * store client, which a Custom Tab / `SFSafariViewController` would not do.
     *
     * @param storeId the Android package name, or the numeric App Store id on iOS. Implementations no-op on an
     *   empty id rather than opening something wrong — the iOS id is not known until the app is registered
     *   with App Store Connect.
     */
    fun openStoreListing(storeId: String)
}

/**
 * No-op fallback so `@Preview` composables that read [LocalPlatformActions] don't crash
 * before a real binding is installed at the host (MainActivity / iOS entry).
 */
object NoOpPlatformActions : PlatformActions {
    override fun share(text: String) = Unit
    override fun openInApp(url: String) = Unit
    override fun openAppNotificationSettings() = Unit
    override fun openStoreListing(storeId: String) = Unit
}

val LocalPlatformActions = staticCompositionLocalOf<PlatformActions> { NoOpPlatformActions }

@Composable
expect fun rememberPlatformActions(): PlatformActions
