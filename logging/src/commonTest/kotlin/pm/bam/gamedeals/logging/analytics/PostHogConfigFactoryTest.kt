package pm.bam.gamedeals.logging.analytics

import io.github.samuolis.posthog.PostHogConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PostHogConfigFactoryTest {

    /**
     * The GDPR-critical invariant: the SDK MUST start opted out so EU users are never tracked before they
     * consent. Flipping this back to `false` would silently re-enable analytics-on-by-default — pin it.
     */
    @Test
    fun starts_opted_out() {
        assertTrue(configurePostHog(apiKey = "phc_key", debug = false).optOut)
        assertTrue(configurePostHog(apiKey = "phc_key", debug = true).optOut)
    }

    @Test
    fun uses_the_eu_host() {
        assertEquals(PostHogConfig.HOST_EU, configurePostHog(apiKey = "phc_key", debug = false).host)
    }

    /**
     * Every form of SDK auto-capture stays OFF: this is a Compose app (View-hierarchy autocapture can't see
     * tap targets) and disabling the SDK's own emitters guarantees 100% of events flow through our wrapper.
     */
    @Test
    fun all_auto_capture_is_disabled() {
        val config = configurePostHog(apiKey = "phc_key", debug = false)

        assertFalse(config.autocapture)
        assertFalse(config.captureApplicationLifecycleEvents)
        assertFalse(config.captureScreenViews)
        assertFalse(config.captureDeepLinks)
        assertFalse(config.enableExceptionAutocapture)
        assertFalse(config.preloadFeatureFlags)
    }

    @Test
    fun passes_through_api_key_and_debug_flag() {
        val config = configurePostHog(apiKey = "phc_abc123", debug = true)

        assertEquals("phc_abc123", config.apiKey)
        assertTrue(config.debug)
        assertFalse(configurePostHog(apiKey = "phc_abc123", debug = false).debug)
    }
}
