package pm.bam.gamedeals.common.navigation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [SignInPromptController.requestWhenReady] must survive being called before the shell's sign-in host collects. */
@OptIn(ExperimentalCoroutinesApi::class)
class SignInPromptControllerTest {

    @Test
    fun request_before_anyone_collects_is_dropped() = runTest(UnconfinedTestDispatcher()) {
        SignInPromptController.request()
        var received = false
        val collector = launch { SignInPromptController.requests.collect { received = true } }
        advanceUntilIdle()
        assertFalse(received)
        collector.cancel()
    }

    @Test
    fun request_when_ready_waits_for_the_host_then_delivers() = runTest(UnconfinedTestDispatcher()) {
        val requester = launch { SignInPromptController.requestWhenReady() }
        advanceUntilIdle()
        assertTrue(requester.isActive, "should still be waiting while nothing collects")

        val received = async { SignInPromptController.requests.first() }
        advanceUntilIdle()
        assertTrue(received.isCompleted)
        assertTrue(requester.isCompleted)
    }
}
