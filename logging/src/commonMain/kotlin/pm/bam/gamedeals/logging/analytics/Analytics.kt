package pm.bam.gamedeals.logging.analytics

/**
 * App-facing product-analytics seam. Deliberately tiny and SDK-agnostic so feature code never imports
 * analytics-SDK types directly: a concrete provider binding lives behind this interface and is swapped in Koin.
 * No provider is integrated today, so every build binds [NoOpAnalytics] and callers never need to null-check.
 *
 * Manual instrumentation by design — this is a Compose app and View-based autocapture can't see Compose tap
 * targets. [screen] is driven by the nav graph;
 * [capture] by explicit call sites named in [AnalyticsEvents].
 */
interface Analytics {
    /** Records a screen view. [name] must be a stable, PII-free screen identifier (e.g. "Game", "Home"). */
    fun screen(name: String, properties: Map<String, Any> = emptyMap())

    /** Records a product event. [event] is a stable snake_case name from [AnalyticsEvents]. */
    fun capture(event: String, properties: Map<String, Any> = emptyMap())

    /** Associates subsequent events with a stable, anonymised [distinctId] (we use the install id). */
    fun identify(distinctId: String)

    /** Clears the current identity (call on logout). */
    fun reset()

    /**
     * Sets analytics consent. A provider must start **opted out** (GDPR: EU users must opt in), so nothing is
     * sent until this is called with `true`. `false` stops all sending, gating every event funnelled through
     * [capture]/[screen]/[identify] at once.
     */
    fun setConsent(granted: Boolean)
}
