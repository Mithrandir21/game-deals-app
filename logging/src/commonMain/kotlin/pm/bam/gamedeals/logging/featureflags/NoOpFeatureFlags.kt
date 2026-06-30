package pm.bam.gamedeals.logging.featureflags

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * No-op [FeatureFlags] bound whenever no flag provider is configured (no PostHog key — debug builds, previews).
 * Every flag resolves to its [FeatureFlag.default], so gated features show their shipped/default state and
 * callers never have to special-case a missing provider.
 */
object NoOpFeatureFlags : FeatureFlags {
    override fun isEnabled(flag: FeatureFlag): Boolean = flag.default
    override fun observe(flag: FeatureFlag): Flow<Boolean> = flowOf(flag.default)
    override fun refresh() = Unit
}
