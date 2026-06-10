package pm.bam.gamedeals.domain.repositories.cache

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Behaviour test for [RequestCoalescer]: a gate ([CompletableDeferred]) holds the shared block
 * in-flight while a second caller joins, proving identical concurrent calls collapse into one,
 * distinct keys run independently, the key is released on completion, and a failure is shared by
 * every awaiter without tearing down the (`SupervisorJob`-backed) scope.
 *
 * The coalescer runs its shared work on a standalone `SupervisorJob` scope on the test dispatcher —
 * the same shape it expects in production, and one that `advanceUntilIdle()` actually drives (unlike
 * `backgroundScope`).
 *
 * Pure-Kotlin unit test, so it lives in commonTest and runs on Android, JVM, and iOS identically.
 */
class RequestCoalescerTest {

    @Test
    fun concurrent_identical_keys_run_block_once_and_share_the_result() = runTest {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val coalescer = RequestCoalescer<String>(scope)
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        var first: String? = null
        var second: String? = null

        val block: suspend () -> String = {
            calls++
            gate.await()
            "value"
        }

        val j1 = launch { first = coalescer.join("k", block) }
        val j2 = launch { second = coalescer.join("k", block) }

        advanceUntilIdle() // both joins register; block is entered once and parked at the gate
        assertEquals(1, calls)

        gate.complete(Unit)
        j1.join(); j2.join()

        assertEquals("value", first)
        assertEquals("value", second)
        assertEquals(1, calls)

        scope.cancel()
    }

    @Test
    fun distinct_keys_run_independently() = runTest {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val coalescer = RequestCoalescer<String>(scope)
        var calls = 0

        val a = coalescer.join("a") { calls++; "A" }
        val b = coalescer.join("b") { calls++; "B" }

        assertEquals("A", a)
        assertEquals("B", b)
        assertEquals(2, calls)

        scope.cancel()
    }

    @Test
    fun key_is_released_after_completion_so_a_later_join_reruns() = runTest {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val coalescer = RequestCoalescer<String>(scope)
        var calls = 0
        val block: suspend () -> String = { calls++; "v" }

        coalescer.join("k", block) // settles and clears the in-flight entry
        coalescer.join("k", block)

        assertEquals(2, calls)

        scope.cancel()
    }

    @Test
    fun failure_is_shared_by_all_awaiters_without_cancelling_the_scope() = runTest {
        val scope = CoroutineScope(coroutineContext + SupervisorJob())
        val coalescer = RequestCoalescer<String>(scope)
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        var e1: Throwable? = null
        var e2: Throwable? = null

        val block: suspend () -> String = {
            calls++
            gate.await()
            throw RuntimeException("boom")
        }

        val j1 = launch { e1 = runCatching { coalescer.join("k", block) }.exceptionOrNull() }
        val j2 = launch { e2 = runCatching { coalescer.join("k", block) }.exceptionOrNull() }

        advanceUntilIdle()
        assertEquals(1, calls)

        gate.complete(Unit)
        j1.join(); j2.join()

        assertTrue(e1 is RuntimeException)
        assertTrue(e2 is RuntimeException)
        assertEquals(1, calls)
        // The scope survived the failed shared call: a subsequent join still works.
        assertEquals("ok", coalescer.join("k2") { "ok" })

        scope.cancel()
    }
}
