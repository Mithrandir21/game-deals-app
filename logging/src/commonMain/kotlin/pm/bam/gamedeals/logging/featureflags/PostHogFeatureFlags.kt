package pm.bam.gamedeals.logging.featureflags

import io.github.samuolis.posthog.PostHog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * [FeatureFlags] backed by the static PostHog SDK (the samuolis/posthog-kmp wrapper). Requires
 * `PostHog.setup(...)` to have run in the platform entry point first (see `configurePostHog` /
 * GameDealsApplication / MainViewController); until then every read returns the [FeatureFlag.default].
 *
 * PostHog evaluates flags server-side and caches them on device, loading them asynchronously after setup and on
 * each [refresh]. There is no native observer, so [observe] is driven off [snapshots]: a small in-memory map that
 * starts at the catalogue defaults and is replaced wholesale when a [refresh] callback fires. [isEnabled] instead
 * reads the SDK's live cache directly, so it reflects a preload that happened before any [refresh] of ours ran.
 */
internal class PostHogFeatureFlags : FeatureFlags {

    // Seeded with the catalogue defaults so observers get a sensible value before the first load lands; replaced
    // wholesale on each refresh() completion (re-reading every known flag from the SDK's freshly-updated cache).
    private val snapshots = MutableStateFlow(FeatureFlag.entries.associateWith { it.default })

    override fun isEnabled(flag: FeatureFlag): Boolean = PostHog.isFeatureEnabled(flag.key, flag.default)

    override fun observe(flag: FeatureFlag): Flow<Boolean> = snapshots.map { it[flag] ?: flag.default }.distinctUntilChanged()

    override fun refresh() {
        PostHog.reloadFeatureFlags {
            snapshots.value = FeatureFlag.entries.associateWith { PostHog.isFeatureEnabled(it.key, it.default) }
        }
    }
}
