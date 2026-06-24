package pm.bam.gamedeals.logging.analytics

/** No-op [Analytics] bound whenever PostHog is disabled (no API key). Every call is a cheap no-op. */
object NoOpAnalytics : Analytics {
    override fun screen(name: String, properties: Map<String, Any>) = Unit
    override fun capture(event: String, properties: Map<String, Any>) = Unit
    override fun identify(distinctId: String) = Unit
    override fun reset() = Unit
    override fun setConsent(granted: Boolean) = Unit
}
