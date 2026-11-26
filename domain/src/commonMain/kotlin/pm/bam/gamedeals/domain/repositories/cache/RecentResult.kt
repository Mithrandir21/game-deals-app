package pm.bam.gamedeals.domain.repositories.cache

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pm.bam.gamedeals.common.time.Clock

/**
 * Reuses a fetched result for [maxAgeMillis]. Callers are serialised, so a request made while another is in flight waits
 * for it and reuses its result — collapsing the same fetch issued by several app-start paths seconds apart (unlike
 * [RequestCoalescer], which only joins calls that overlap). A failed fetch isn't kept. [invalidate] after a local edit.
 */
internal class RecentResult<T>(
    private val clock: Clock,
    private val maxAgeMillis: Long,
) {
    private class Fetched<T>(val value: T, val atMillis: Long)

    private val mutex = Mutex()
    private var fetched: Fetched<T>? = null

    suspend fun get(fetch: suspend () -> T): T = mutex.withLock {
        fetched?.takeIf { clock.nowMillis() - it.atMillis < maxAgeMillis }?.value
            ?: fetch().also { fetched = Fetched(it, clock.nowMillis()) }
    }

    suspend fun invalidate() = mutex.withLock { fetched = null }
}

/** How long a waitlist/collection download is reused — long enough to cover app start, short enough to stay current. */
internal const val LIBRARY_REUSE_MILLIS = 30L * 1000
