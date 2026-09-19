package pm.bam.gamedeals.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import pm.bam.gamedeals.logging.featureflags.FeatureFlag
import pm.bam.gamedeals.logging.featureflags.FeatureFlags

/**
 * In-memory [FeatureFlags] for tests. Backed by a [MutableStateFlow] so a test can flip a flag with [set] and
 * assert that an [observe] collector (e.g. a ViewModel `StateFlow`) reacts. Unset flags resolve to their
 * [FeatureFlag.default]. [refreshCount] records how many times [refresh] was called.
 *
 * Payloads are set independently of the on/off state via [setPayload], because the two are independent in
 * remote flag providers too — a flag can be enabled with no payload, or carry a stale payload while disabled. Tests for
 * payload-driven features should exercise both axes.
 */
class FakeFeatureFlags(
    initial: Map<FeatureFlag, Boolean> = emptyMap(),
    initialPayloads: Map<FeatureFlag, String> = emptyMap(),
) : FeatureFlags {

    private val states = MutableStateFlow(FeatureFlag.entries.associateWith { initial[it] ?: it.default })

    private val payloads = MutableStateFlow<Map<FeatureFlag, String?>>(initialPayloads)

    var refreshCount: Int = 0
        private set

    /** Set [flag] to [enabled], emitting to any active [observe] collectors. */
    fun set(flag: FeatureFlag, enabled: Boolean) = states.update { it + (flag to enabled) }

    /** Set [flag]'s raw JSON payload (or `null` to clear it), emitting to any active [observePayload] collectors. */
    fun setPayload(flag: FeatureFlag, json: String?) = payloads.update { it + (flag to json) }

    override fun isEnabled(flag: FeatureFlag): Boolean = states.value[flag] ?: flag.default

    override fun observe(flag: FeatureFlag): Flow<Boolean> =
        states.map { it[flag] ?: flag.default }.distinctUntilChanged()

    override fun payload(flag: FeatureFlag): String? = payloads.value[flag]

    override fun observePayload(flag: FeatureFlag): Flow<String?> =
        payloads.map { it[flag] }.distinctUntilChanged()

    override fun refresh() {
        refreshCount++
    }
}
