package pm.bam.gamedeals.common.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow

/**
 * This deals with single event effects that are not expected to be collected more than once,
 * specifically for side effects that are not intended to be recomposed, such as navigation events, snackbar messages, analytics, etc.
 *
 * If events are emitted *after* the lifecycle state has changed from [Lifecycle.State.STARTED] and [Lifecycle.State.RESUMED], they will be ignored.
 *
 * Note that because the provided [sideEffectFlow] itself is being used as the key for the [LaunchedEffect],
 * repeating the same data ***will*** trigger the [collector] again.
 *
 * The [collector] lambda is wrapped via [rememberUpdatedState], so callers may safely close over
 * screen-local state (navigation controllers, analytics payloads, etc.) without observing a
 * stale capture from the first composition.
 *
 * @param sideEffectFlow The flow of side effects to collect.
 * @param lifeCycleState The lifecycle state to collect the side effects in. Default is [Lifecycle.State.STARTED].
 * @param collector The collector to handle the side effects.
 */
@Composable
fun <T : Any> SingleEventEffect(
    sideEffectFlow: Flow<T>,
    lifeCycleState: Lifecycle.State = Lifecycle.State.STARTED,
    collector: suspend (T) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentCollector by rememberUpdatedState(collector)

    LaunchedEffect(sideEffectFlow, lifecycleOwner, lifeCycleState) {
        lifecycleOwner.repeatOnLifecycle(lifeCycleState) {
            sideEffectFlow.collect { currentCollector(it) }
        }
    }
}
