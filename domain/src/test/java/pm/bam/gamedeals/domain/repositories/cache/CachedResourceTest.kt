package pm.bam.gamedeals.domain.repositories.cache

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pm.bam.gamedeals.common.time.Clock

/**
 * Boundary test for [CachedResource]: a fake [Clock] proves that an expired entry triggers
 * refresh and a fresh entry does not, without touching Room or any Android API.
 *
 * Picks a JVM unit test (over `androidTest` + in-memory Room) on purpose: [CachedResource] is
 * Flow-/Room-free, so a JVM test is faster, deterministic, and avoids pulling in Robolectric.
 */
class CachedResourceTest {

    private data class Entry(val id: Int, val expires: Long)

    private class MutableClock(initial: Long) : Clock {
        var nowMillis: Long = initial
        override fun nowMillis(): Long = nowMillis
    }

    @Test
    fun `empty cache - refreshIfNeeded - invokes refresh once and returns true`() = runTest {
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
    fun `fresh cache - refreshIfNeeded - skips refresh and returns false`() = runTest {
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
    fun `expired entry - refreshIfNeeded - invokes refresh and returns true`() = runTest {
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
    fun `mixed entries - any expired triggers refresh`() = runTest {
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
    fun `boundary - entry whose expires equals now is still fresh`() = runTest {
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
    fun `same cache - clock advances past expiry - second call refreshes`() = runTest {
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
    fun `force - true - bypasses freshness check and always refreshes`() = runTest {
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
}
