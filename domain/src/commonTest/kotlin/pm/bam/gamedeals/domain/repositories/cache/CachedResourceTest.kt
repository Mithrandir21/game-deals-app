package pm.bam.gamedeals.domain.repositories.cache

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.common.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Boundary test for [CachedResource]: a fake [Clock] proves that an expired entry triggers
 * refresh and a fresh entry does not, without touching Room or any platform API.
 *
 * Pure-Kotlin unit test (Flow-/Room-free), so it lives in commonTest and runs on Android,
 * JVM, and iOS targets identically.
 */
class CachedResourceTest {

    private data class Entry(val id: Int, val expires: Long)

    private class MutableClock(initial: Long) : Clock {
        var nowMillis: Long = initial
        override fun nowMillis(): Long = nowMillis
    }

    @Test
    fun empty_cache_refreshIfNeeded_invokes_refresh_once_and_returns_true() = runTest {
        val clock = MutableClock(initial = 1_000)
        var refreshCount = 0
        val cache = CachedResource(
            clock = clock,
            read = { emptyList<Entry>() },
            expiresAtMillis = { it.expires },
            refresh = { refreshCount++ },
        )

        val refreshed = cache.refreshIfNeeded()

        assertTrue(refreshed)
        assertEquals(1, refreshCount)
    }

    @Test
    fun fresh_cache_refreshIfNeeded_skips_refresh_and_returns_false() = runTest {
        val clock = MutableClock(initial = 1_000)
        var refreshCount = 0
        val cache = CachedResource(
            clock = clock,
            read = { listOf(Entry(id = 1, expires = clock.nowMillis + 10_000)) },
            expiresAtMillis = { it.expires },
            refresh = { refreshCount++ },
        )

        val refreshed = cache.refreshIfNeeded()

        assertFalse(refreshed)
        assertEquals(0, refreshCount)
    }

    @Test
    fun expired_entry_refreshIfNeeded_invokes_refresh_and_returns_true() = runTest {
        val clock = MutableClock(initial = 1_000)
        var refreshCount = 0
        val cache = CachedResource(
            clock = clock,
            // entry was valid until 999ms ago
            read = { listOf(Entry(id = 1, expires = clock.nowMillis - 1)) },
            expiresAtMillis = { it.expires },
            refresh = { refreshCount++ },
        )

        val refreshed = cache.refreshIfNeeded()

        assertTrue(refreshed)
        assertEquals(1, refreshCount)
    }

    @Test
    fun mixed_entries_any_expired_triggers_refresh() = runTest {
        val clock = MutableClock(initial = 1_000)
        var refreshCount = 0
        val cache = CachedResource(
            clock = clock,
            read = {
                listOf(
                    Entry(id = 1, expires = clock.nowMillis + 10_000), // fresh
                    Entry(id = 2, expires = clock.nowMillis - 1),      // expired
                )
            },
            expiresAtMillis = { it.expires },
            refresh = { refreshCount++ },
        )

        val refreshed = cache.refreshIfNeeded()

        assertTrue(refreshed)
        assertEquals(1, refreshCount)
    }

    @Test
    fun boundary_entry_whose_expires_equals_now_is_still_fresh() = runTest {
        // Matches original `expires < currentTimeMillis()` semantics — equality is not yet expired.
        val clock = MutableClock(initial = 1_000)
        var refreshCount = 0
        val cache = CachedResource(
            clock = clock,
            read = { listOf(Entry(id = 1, expires = clock.nowMillis)) },
            expiresAtMillis = { it.expires },
            refresh = { refreshCount++ },
        )

        val refreshed = cache.refreshIfNeeded()

        assertFalse(refreshed)
        assertEquals(0, refreshCount)
    }

    @Test
    fun same_cache_clock_advances_past_expiry_second_call_refreshes() = runTest {
        val clock = MutableClock(initial = 1_000)
        val expiresAt = 5_000L
        var refreshCount = 0
        val cache = CachedResource(
            clock = clock,
            read = { listOf(Entry(id = 1, expires = expiresAt)) },
            expiresAtMillis = { it.expires },
            refresh = { refreshCount++ },
        )

        // before expiry
        val first = cache.refreshIfNeeded()
        // advance the fake clock past the entry's expiry
        clock.nowMillis = expiresAt + 1
        val second = cache.refreshIfNeeded()

        assertFalse(first)
        assertTrue(second)
        assertEquals(1, refreshCount)
    }

    @Test
    fun force_true_bypasses_freshness_check_and_always_refreshes() = runTest {
        val clock = MutableClock(initial = 1_000)
        var refreshCount = 0
        var readCount = 0
        val cache = CachedResource(
            clock = clock,
            read = {
                readCount++
                listOf(Entry(id = 1, expires = clock.nowMillis + 10_000))
            },
            expiresAtMillis = { it.expires },
            refresh = { refreshCount++ },
        )

        val refreshed = cache.refreshIfNeeded(force = true)

        assertTrue(refreshed)
        assertEquals(1, refreshCount)
        // force avoids the read-and-compare path entirely
        assertEquals(0, readCount)
    }

    @Test
    fun refresh_failure_with_nonempty_cache_is_swallowed_and_serves_stale() = runTest {
        val clock = MutableClock(initial = 1_000)
        // expired entry: a refresh is attempted, but the cache is non-empty
        val cache = CachedResource(
            clock = clock,
            read = { listOf(Entry(id = 1, expires = clock.nowMillis - 1)) },
            expiresAtMillis = { it.expires },
            refresh = { throw RuntimeException("network down") },
        )

        // No exception propagates; the stale entry is kept (serve-stale-on-error).
        val refreshed = cache.refreshIfNeeded()

        assertTrue(refreshed) // refresh was attempted
    }

    @Test
    fun refresh_failure_with_empty_cache_rethrows() = runTest {
        val clock = MutableClock(initial = 1_000)
        val cache = CachedResource(
            clock = clock,
            read = { emptyList<Entry>() },
            expiresAtMillis = { it.expires },
            refresh = { throw RuntimeException("network down") },
        )

        // Nothing to fall back to, so the failure must surface.
        assertFailsWith<RuntimeException> { cache.refreshIfNeeded() }
    }

    @Test
    fun refresh_failure_with_surfaceErrors_rethrows_even_with_cached_data() = runTest {
        val clock = MutableClock(initial = 1_000)
        val cache = CachedResource(
            clock = clock,
            read = { listOf(Entry(id = 1, expires = clock.nowMillis - 1)) },
            expiresAtMillis = { it.expires },
            refresh = { throw RuntimeException("network down") },
        )

        // surfaceErrors opts a pull-to-refresh caller into seeing the failure despite stale rows.
        assertFailsWith<RuntimeException> { cache.refreshIfNeeded(surfaceErrors = true) }
    }

    @Test
    fun refresh_cancellation_is_always_rethrown() = runTest {
        val clock = MutableClock(initial = 1_000)
        val cache = CachedResource(
            clock = clock,
            read = { listOf(Entry(id = 1, expires = clock.nowMillis - 1)) },
            expiresAtMillis = { it.expires },
            refresh = { throw CancellationException("cancelled") },
        )

        // Cancellation must never be swallowed by serve-stale-on-error.
        assertFailsWith<CancellationException> { cache.refreshIfNeeded() }
    }
}
