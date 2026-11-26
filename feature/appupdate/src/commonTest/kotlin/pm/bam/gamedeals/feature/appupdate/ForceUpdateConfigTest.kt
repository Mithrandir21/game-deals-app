package pm.bam.gamedeals.feature.appupdate

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Direct tests for the payload parse. `AppUpdateViewModelTest` already covers the *decision* these feed, but
 * this is the boundary where untrusted remote text enters the app, so it is worth pinning on its own: the
 * failure modes are a hand-edited JSON blob in a flag provider's dashboard, and the only recovery from a bad one is
 * shipping a release.
 */
class ForceUpdateConfigTest {

    @Test
    fun `a complete payload parses`() {
        val config = parseForceUpdateConfig("""{"minimum_version":"1.2.0","blocking":true}""")

        assertEquals("1.2.0", config?.minimumVersion)
        assertEquals(true, config?.blocking)
    }

    @Test
    fun `blocking defaults to false when omitted`() {
        // Forgetting the field must give the nudge, never the lockout.
        assertEquals(false, parseForceUpdateConfig("""{"minimum_version":"1.2.0"}""")?.blocking)
    }

    @Test
    fun `unknown keys are ignored so the payload can grow server-side`() {
        val config = parseForceUpdateConfig("""{"minimum_version":"1.2.0","message":"hi","rollout":0.5}""")

        assertEquals("1.2.0", config?.minimumVersion)
    }

    @Test
    fun `null and blank input yield no config`() {
        assertNull(parseForceUpdateConfig(null))
        assertNull(parseForceUpdateConfig(""))
        assertNull(parseForceUpdateConfig("   "))
    }

    @Test
    fun `malformed json yields no config rather than throwing`() {
        assertNull(parseForceUpdateConfig("{"))
        assertNull(parseForceUpdateConfig("not json at all"))
        assertNull(parseForceUpdateConfig("""{"minimum_version":}"""))
    }

    @Test
    fun `a payload of the wrong shape yields no config`() {
        // A bare array or scalar where an object was expected — e.g. the flag payload set to the wrong type.
        assertNull(parseForceUpdateConfig("""["1.2.0"]"""))
        assertNull(parseForceUpdateConfig("\"1.2.0\""))
        assertNull(parseForceUpdateConfig("true"))
    }

    @Test
    fun `a missing minimum_version yields no config`() {
        // The field is required, so a payload carrying only `blocking` must not arm a gate with no floor.
        assertNull(parseForceUpdateConfig("""{"blocking":true}"""))
    }

    @Test
    fun `a blank minimum_version yields no config`() {
        // Present but empty is the likeliest hand-edit slip, and "" would compare below every version.
        assertNull(parseForceUpdateConfig("""{"minimum_version":"","blocking":true}"""))
        assertNull(parseForceUpdateConfig("""{"minimum_version":"   "}"""))
    }

    @Test
    fun `a wrongly-typed minimum_version yields no config`() {
        // JSON number instead of string — kotlinx is strict here, and the runCatching must absorb it.
        assertNull(parseForceUpdateConfig("""{"minimum_version":1.2}"""))
    }

    @Test
    fun `a quoted boolean is coerced rather than rejected`() {
        // kotlinx accepts a JSON string for a Boolean field and coerces it. Pinned because it is asymmetric
        // with the quoted-number case above (which is rejected) and is therefore easy to "fix" by mistake.
        // The coercion is the safer of the two outcomes here: an operator who typed "true" meant true, and
        // rejecting the payload outright would silently disarm a gate they believed they had armed.
        assertEquals(true, parseForceUpdateConfig("""{"minimum_version":"1.2.0","blocking":"true"}""")?.blocking)

        // Non-boolean text is still rejected, so the coercion can't turn arbitrary junk into a hard gate.
        assertNull(parseForceUpdateConfig("""{"minimum_version":"1.2.0","blocking":"yes"}"""))
    }

    @Test
    fun `an unparseable version string still parses here and is rejected downstream`() {
        // The parse only guarantees shape, not semver validity — the version comparison fails open instead.
        // Pinned so the split of responsibility stays visible if either side is ever changed.
        val config = parseForceUpdateConfig("""{"minimum_version":"banana"}""")

        assertTrue(config != null)
        assertEquals("banana", config.minimumVersion)
    }
}
