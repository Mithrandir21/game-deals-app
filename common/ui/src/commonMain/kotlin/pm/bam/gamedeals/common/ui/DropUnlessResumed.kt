package pm.bam.gamedeals.common.ui

import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * The argument-taking counterpart of androidx's `dropUnlessResumed { }`: the returned callback runs this one
 * only while the current screen (the composition's [LocalLifecycleOwner], its back-stack entry inside the
 * NavHost) is resumed. A screen stops being resumed as soon as a navigation away from it starts, so a second
 * tap during the transition is dropped instead of opening the same page twice.
 *
 * Wrap only callbacks a tap triggers: a push made from an effect can run before the screen has resumed.
 */
@Composable
fun <T> ((T) -> Unit).dropUnlessResumed(): (T) -> Unit = dropUnlessResumed(LocalLifecycleOwner.current.lifecycle)

/** [dropUnlessResumed] for two-argument callbacks, such as `goToWeb(url, title)`. */
@Composable
fun <A, B> ((A, B) -> Unit).dropUnlessResumed(): (A, B) -> Unit = dropUnlessResumed(LocalLifecycleOwner.current.lifecycle)

internal fun <T> ((T) -> Unit).dropUnlessResumed(lifecycle: Lifecycle): (T) -> Unit {
    val block = this
    return { value -> if (lifecycle.isResumed) block(value) }
}

internal fun <A, B> ((A, B) -> Unit).dropUnlessResumed(lifecycle: Lifecycle): (A, B) -> Unit {
    val block = this
    return { a, b -> if (lifecycle.isResumed) block(a, b) }
}

private val Lifecycle.isResumed: Boolean
    get() = currentState.isAtLeast(Lifecycle.State.RESUMED)
