package pm.bam.gamedeals.logging.featureflags

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The provider bound in *every build* while no remote flag provider is integrated, so it is the
 * implementation users and developers actually run against. Its whole contract is "hand back the catalogue
 * default and nothing else"; a regression that returned `true`, or a non-null payload, would quietly enable
 * unreleased features locally and make the flag seam look like it worked when it did not.
 */
class NoOpFeatureFlagsTest {

    @Test
    fun `every flag resolves to its catalogue default`() {
        // Iterating the catalogue rather than naming flags means a newly added flag is covered on day one.
        FeatureFlag.entries.forEach { flag ->
            assertEquals(flag.default, NoOpFeatureFlags.isEnabled(flag), flag.key)
        }
    }

    @Test
    fun `observing a flag emits its default`() = runTest {
        FeatureFlag.entries.forEach { flag ->
            assertEquals(flag.default, NoOpFeatureFlags.observe(flag).first(), flag.key)
        }
    }

    @Test
    fun `no flag ships defaulted on`() {
        // A default of `true` would ship the feature to everyone the moment it merged, with the remote flag
        // able only to turn it *off* — the opposite of a staged rollout. Deliberate exceptions belong here.
        // DiscoverByTag ships on because no remote provider is integrated to roll it out.
        val shippedOn = setOf(FeatureFlag.DiscoverByTag)
        FeatureFlag.entries.filterNot { it in shippedOn }.forEach { flag ->
            assertEquals(false, flag.default, "${flag.key} defaults to on")
        }
    }

    @Test
    fun `flag keys are unique and non-blank`() {
        // Two entries sharing a key would silently alias to one remote flag.
        val keys = FeatureFlag.entries.map { it.key }

        assertEquals(keys.size, keys.toSet().size, "duplicate flag keys: $keys")
        assertEquals(emptyList(), keys.filter { it.isBlank() })
    }

    @Test
    fun `there is never a payload without a provider`() {
        FeatureFlag.entries.forEach { flag ->
            assertNull(NoOpFeatureFlags.payload(flag), flag.key)
        }
    }

    @Test
    fun `observing a payload emits null`() = runTest {
        FeatureFlag.entries.forEach { flag ->
            assertNull(NoOpFeatureFlags.observePayload(flag).first(), flag.key)
        }
    }

    @Test
    fun `refresh is a no-op rather than a failure`() {
        // Called unconditionally at startup by every entry point, provider or not.
        NoOpFeatureFlags.refresh()
    }
}
