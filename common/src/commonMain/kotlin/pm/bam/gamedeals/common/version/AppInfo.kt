package pm.bam.gamedeals.common.version

/**
 * Platform-sourced identity of the running build, registered in Koin by the app entry point (BuildConfig on
 * Android, Info.plist on iOS) — the same "the platform provides it at the boundary" pattern as
 * `IgdbCredentials`, `ItadCredentials` and `AnalyticsConfig`.
 *
 * This exists separately from `AnalyticsConfig.appVersion`, which already carries the same version string,
 * because the minimum-version gate is not an analytics concern: reading a config object from the analytics
 * namespace to decide whether to block the app would couple two things that should be free to diverge.
 *
 * @property versionName the human semantic version ("1.1.3"). Compared against the remote floor by
 *   [isBelowMinimum]; the one field that must be a real semver string on both platforms.
 * @property versionCode the monotonic build number — Android `versionCode`, iOS `CFBundleVersion`. Not used
 *   for gating (the two platforms occupy different number spaces); carried for diagnostics.
 * @property storeId identifies this app to its store listing: the Android package name, or the numeric App
 *   Store id on iOS. **May be empty** — the iOS id isn't known until the app is registered with App Store
 *   Connect — so anything opening a store listing must degrade gracefully rather than assume it is set.
 * @property isDebug whether this is a debug build. Used to gate developer-only affordances (see the
 *   minimum-version gate's local override); release builds must never take a path guarded by this.
 */
data class AppInfo(
    val versionName: String,
    val versionCode: Long,
    val storeId: String,
    val isDebug: Boolean,
)
