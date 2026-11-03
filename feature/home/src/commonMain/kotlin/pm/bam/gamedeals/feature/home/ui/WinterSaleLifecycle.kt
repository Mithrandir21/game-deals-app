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
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) currentResume.value()
        onDispose { owner.lifecycle.removeObserver(observer); currentPause.value() }
    }
}
