package pm.bam.gamedeals.logging.analytics

import io.github.samuolis.posthog.PostHog

/**
 * [Analytics] backed by the static PostHog SDK. Requires `PostHog.setup(...)` to have run in the platform
 * entry point first (see [configurePostHog] / GameDealsApplication). [baseProperties] (environment, app
 * version) are merged into every event so 100% of our events are env-tagged — the SDK is configured to emit
 * nothing on its own (no autocapture/lifecycle/screen views), so there are no untagged events to worry about.
 */
internal class PostHogAnalytics(
    private val baseProperties: Map<String, Any>,
) : Analytics {
    override fun screen(name: String, properties: Map<String, Any>) {
        PostHog.screen(name, baseProperties + properties)
    }

    override fun capture(event: String, properties: Map<String, Any>) {
        PostHog.capture(event, baseProperties + properties)
    }

    override fun identify(distinctId: String) {
        PostHog.identify(distinctId)
    }

    override fun reset() {
        PostHog.reset()
    }

    override fun setConsent(granted: Boolean) {
        if (granted) PostHog.optIn() else PostHog.optOut()
    }
}
