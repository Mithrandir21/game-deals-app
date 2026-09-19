package pm.bam.gamedeals.logging.analytics

/**
 * Platform-sourced analytics configuration, registered in Koin by the app entry point — mirrors how
 * `IgdbCredentials` / `ItadCredentials` are provided. Carries the base properties a future [Analytics]
 * provider stamps on every event; the current [NoOpAnalytics] binding ignores it.
 *
 * @property environment "debug" or "release" — stamped on every event so dev noise is filterable.
 * @property appVersion the app's versionName — stamped on every event.
 */
data class AnalyticsConfig(
    val environment: String,
    val appVersion: String,
) {
    /** Properties merged into every [Analytics.screen] / [Analytics.capture] call (super-property substitute). */
    fun baseProperties(): Map<String, Any> = mapOf(
        "environment" to environment,
        "app_version" to appVersion,
    )
}
