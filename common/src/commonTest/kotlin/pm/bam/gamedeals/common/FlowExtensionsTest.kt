package pm.bam.gamedeals.common

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies the virtual-time correctness of the `*DelayAtLeast` Flow operators.
 *
 * The previous implementation relied on `System.testScheduler.currentTimeMillis()` to measure
 * the duration of the inner transformation, which does not honour the test
 * dispatcher's virtual clock. Under `runTest` the measured duration was always
 * ~0, so the operator always padded to the full `delayMillis`.
 *
 * The new implementation uses only `delay`, so behaviour matches between
 * production wall-clock and virtual time: the operator emits after at least
 * `delayMillis`, or after the inner transformation completes, whichever is later.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class FlowExtensionsTest {

    @Test
    fun mapDelayAtLeast_pads_to_delayMillis_when_transform_is_instant() = runTest {
        val start = testScheduler.currentTime
        val results = flowOf(1)
            .mapDelayAtLeast(DELAY_MILLIS) { it * 2 }
            .toList()
        val elapsed = testScheduler.currentTime - start

        assertEquals(listOf(2), results)
        assertEquals(DELAY_MILLIS, elapsed)
    }

    @Test
    fun mapDelayAtLeast_does_not_pad_when_transform_takes_longer_than_delayMillis() = runTest {
        val start = testScheduler.currentTime
        val results = flowOf(1)
            .mapDelayAtLeast(DELAY_MILLIS) { value ->
                delay(LONG_WORK_MILLIS)
                value * 2
            }
            .toList()
        val elapsed = testScheduler.currentTime - start

        assertEquals(listOf(2), results)
        assertEquals(LONG_WORK_MILLIS, elapsed)
    }

    @Test
    fun flatMapLatestDelayAtLeast_pads_to_delayMillis_when_transform_is_instant() = runTest {
        val start = testScheduler.currentTime
        val results = flowOf(1)
            .flatMapLatestDelayAtLeast(DELAY_MILLIS) { it * 2 }
            .toList()
        val elapsed = testScheduler.currentTime - start

        assertEquals(listOf(2), results)
        assertEquals(DELAY_MILLIS, elapsed)
    }

    @Test
    fun flatMapLatestDelayAtLeast_does_not_pad_when_transform_takes_longer_than_delayMillis() = runTest {
        val start = testScheduler.currentTime
        val results = flowOf(1)
            .flatMapLatestDelayAtLeast(DELAY_MILLIS) { value ->
                delay(LONG_WORK_MILLIS)
                value * 2
            }
            .toList()
        val elapsed = testScheduler.currentTime - start

        assertEquals(listOf(2), results)
        assertEquals(LONG_WORK_MILLIS, elapsed)
    }

    @Test
    fun latestDelayAtLeast_emits_after_delayMillis_virtual_time() = runTest {
        val start = testScheduler.currentTime
        val results = flowOf(42)
            .latestDelayAtLeast(DELAY_MILLIS)
            .toList()
        val elapsed = testScheduler.currentTime - start

        assertEquals(listOf(42), results)
        assertEquals(DELAY_MILLIS, elapsed)
    }

    @Test
    fun withMinimumDuration_pads_to_delayMillis_when_block_is_instant() = runTest {
        val start = testScheduler.currentTime
        val result = withMinimumDuration(DELAY_MILLIS) { 21 * 2 }
        val elapsed = testScheduler.currentTime - start

        assertEquals(42, result)
        assertEquals(DELAY_MILLIS, elapsed)
    }

    @Test
    fun withMinimumDuration_does_not_pad_when_block_takes_longer_than_delayMillis() = runTest {
        val start = testScheduler.currentTime
        val result = withMinimumDuration(DELAY_MILLIS) {
            delay(LONG_WORK_MILLIS)
            21 * 2
        }
        val elapsed = testScheduler.currentTime - start

        assertEquals(42, result)
        assertEquals(LONG_WORK_MILLIS, elapsed)
    }

    @Test
    fun latestDelayAtLeast_cancels_pending_pad_when_a_new_value_arrives() = runTest {
        // Emits 1 immediately, then 2 after `delayMillis * 2`. Because
        // `latestDelayAtLeast` uses `transformLatest`, the in-flight pad for
        // value 1 should be cancelled when value 2 arrives, so only value 2 is
        // emitted (after its own pad).
        val start = testScheduler.currentTime
        val results = flow {
            emit(1)
            delay(DELAY_MILLIS / 2)
            emit(2)
        }
            .latestDelayAtLeast(DELAY_MILLIS)
            .toList()
        val elapsed = testScheduler.currentTime - start

        assertEquals(listOf(2), results)
        // Value 2 arrives at DELAY_MILLIS / 2, then pads for DELAY_MILLIS.
        assertEquals(DELAY_MILLIS / 2 + DELAY_MILLIS, elapsed)
    }

    private companion object {
        const val DELAY_MILLIS = 1_000L
        const val LONG_WORK_MILLIS = 3_000L
    }
}
