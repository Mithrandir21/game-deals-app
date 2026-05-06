package pm.bam.gamedeals.common.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.Flow

// Events emitted while the lifecycle is below [lifeCycleState] are dropped. The flow is the
// LaunchedEffect key, so re-emitting the same value re-triggers the collector.
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
