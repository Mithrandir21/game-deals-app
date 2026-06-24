package pm.bam.gamedeals.domain

import pm.bam.gamedeals.logging.analytics.Analytics

/**
 * Shared test [Analytics] that records every captured event + its properties, so repository tests can assert
 * both the event name and the payload. [events] keeps the bare name list for tests that only care about that.
 */
internal class RecordingAnalytics : Analytics {
    val events = mutableListOf<String>()
    val captured = mutableListOf<Pair<String, Map<String, Any>>>()

    override fun screen(name: String, properties: Map<String, Any>) = Unit
    override fun capture(event: String, properties: Map<String, Any>) {
        events += event
        captured += event to properties
    }
    override fun identify(distinctId: String) = Unit
    override fun reset() = Unit
    override fun setConsent(granted: Boolean) = Unit

    /** Properties of the last capture of [event], or null if it was never captured. */
    fun propsOf(event: String): Map<String, Any>? = captured.lastOrNull { it.first == event }?.second
}
