package pm.bam.gamedeals.common.ui

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Names avoid backticks-with-spaces: commonTest classes are also dexed into this module's androidDeviceTest
 * APK, and D8 rejects spaces in method names below DEX 040.
 */
class DropUnlessResumedTest {

    private val owner = object : LifecycleOwner {
        override val lifecycle: LifecycleRegistry = LifecycleRegistry.createUnsafe(this)
    }

    @Test
    fun runs_while_the_screen_is_resumed() {
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        val opened = mutableListOf<String>()

        val open = { id: String -> opened += id }.dropUnlessResumed(owner.lifecycle)
        open("a")

        assertEquals(listOf("a"), opened)
    }

    @Test
    fun drops_the_tap_once_the_screen_is_leaving() {
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        val opened = mutableListOf<String>()
        val open = { id: String -> opened += id }.dropUnlessResumed(owner.lifecycle)

        open("a")
        // Navigating away moves the screen out of RESUMED for the rest of the transition.
        owner.lifecycle.currentState = Lifecycle.State.STARTED
        open("a")

        assertEquals(listOf("a"), opened)
    }

    @Test
    fun two_argument_callbacks_are_guarded_too() {
        owner.lifecycle.currentState = Lifecycle.State.STARTED
        val opened = mutableListOf<Pair<String, String>>()
        val open = { url: String, title: String -> opened += url to title }.dropUnlessResumed(owner.lifecycle)

        open("https://a", "A")
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        open("https://b", "B")

        assertEquals(listOf("https://b" to "B"), opened)
    }
}
