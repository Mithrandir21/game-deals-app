package pm.bam.gamedeals.feature.home.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@Composable
internal fun WinterSaleResumeEffect(onResume: () -> Unit, onPause: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    val currentResume = rememberUpdatedState(onResume)
    val currentPause = rememberUpdatedState(onPause)
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> currentResume.value()
                Lifecycle.Event.ON_PAUSE -> currentPause.value()
                else -> Unit
            }
        }
        // addObserver replays the events up to the current state, so an already-resumed screen gets ON_RESUME here.
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); currentPause.value() }
    }
}
