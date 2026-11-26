package pm.bam.gamedeals.logging.featureflags

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * No-op [FeatureFlags]: every flag resolves to its [FeatureFlag.default], so gated features show their shipped
 * state. For tests and previews; the apps bind the switch-file reader.
 */
object NoOpFeatureFlags : FeatureFlags {
    override fun isEnabled(flag: FeatureFlag): Boolean = flag.default
    override fun observe(flag: FeatureFlag): Flow<Boolean> = flowOf(flag.default)
    // No provider means no remote payload. Callers must already treat a null payload as "no configuration",
    // so there is nothing sensible to substitute here.
    override fun payload(flag: FeatureFlag): String? = null
    override fun observePayload(flag: FeatureFlag): Flow<String?> = flowOf(null)
    override fun refresh() = Unit
    override suspend fun awaitRefresh() = Unit
}
