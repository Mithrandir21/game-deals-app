package pm.bam.gamedeals.logging.featureflags

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The catalogue's own rules, and the no-op provider used by tests and previews. Its whole contract is "hand back the
 * catalogue default and nothing else"; the apps' switch-file reader falls back to the same defaults.
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
        // A default of `true` ships the feature to everyone the moment it merges, with the switch file able only to
        // turn it *off*. Deliberate exceptions belong here: the shipped features, which a switch can turn off in an
        // emergency. force_update must stay off, or every phone would be asked to update before its first load.
        val shippedOn = setOf(
            FeatureFlag.DiscoverByTag, FeatureFlag.YearInDeals, FeatureFlag.DealLinks, FeatureFlag.WishlistLinks,
            FeatureFlag.DealSwipe, FeatureFlag.SaleCountdown, FeatureFlag.GameSignals, FeatureFlag.BundleWatch,
        )
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
    fun `flag keys follow the switch file's key rule`() {
        // store/site/flags-routes.mjs answers 503 for a file with any other key, so a key outside this rule could
        // never be set.
        val rule = Regex("^[a-z][a-z0-9_]{0,63}$")
        assertEquals(emptyList(), FeatureFlag.entries.map { it.key }.filterNot { rule.matches(it) })
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
    fun `refresh is a no-op rather than a failure`() = runTest {
        NoOpFeatureFlags.refresh()
        NoOpFeatureFlags.awaitRefresh()
    }
}
