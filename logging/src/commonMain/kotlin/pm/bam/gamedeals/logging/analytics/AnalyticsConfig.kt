package pm.bam.gamedeals.logging.analytics

/**
 * Platform-sourced analytics configuration, registered in Koin by the app entry point (BuildConfig on
 * Android, Info.plist on iOS) — mirrors how `IgdbCredentials` / `ItadCredentials` are provided. The
 * :logging Koin module reads this to decide [NoOpAnalytics] vs [PostHogAnalytics] and to build the base
 * properties stamped on every event.
 *
 * @property apiKey the PostHog `phc_…` project key; empty disables analytics (binds [NoOpAnalytics]).
 * @property environment "debug" or "release" — stamped on every event so dev noise is filterable.
 * @property appVersion the app's versionName — stamped on every event.
 */
data class AnalyticsConfig(
    val apiKey: String,
    val environment: String,
    val appVersion: String,
) {
    /** Properties merged into every [Analytics.screen] / [Analytics.capture] call (super-property substitute). */
    fun baseProperties(): Map<String, Any> = mapOf(
        "environment" to environment,
        "app_version" to appVersion,
    )
}
