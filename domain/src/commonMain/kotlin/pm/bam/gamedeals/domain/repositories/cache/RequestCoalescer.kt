package pm.bam.gamedeals.domain.repositories.cache

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-flight de-duplication ("request coalescing") for identical concurrent calls.
 *
 * Several callers can ask for the same resource at the same moment — e.g. a deals list and the
 * individual tiles it renders each firing the same `/games/prices/v3` refresh. Routed through
 * [join] under the same [key], only the **first** call runs [block]; every concurrent caller awaits
 * that one shared [Deferred] and receives its result (or its failure). This collapses N identical
 * refreshes into a single network call without the callers needing to know about each other.
 *
 * The key should capture everything that makes a call identical — for ITAD that is the tuple
 * `(resource, key, country)` (see the caching strategy, §7). Distinct keys run fully independently.
 *
 * **Lifetime & cancellation:** the shared work is launched on the injected [scope], not on any one
 * caller's coroutine, so a single caller cancelling its `await()` does not cancel the in-flight call
 * for the others. The [scope] **must** be backed by a [kotlinx.coroutines.SupervisorJob] so a failed
 * shared call surfaces through `await()` (to every awaiter) instead of cancelling the scope. Provide
 * a scope whose lifetime matches the cache owner (e.g. an application/repository scope).
 *
 * Pure Kotlin, `commonMain`, Android-free — mirroring [CachedResource].
 *
 * @param scope The [SupervisorJob][kotlinx.coroutines.SupervisorJob]-backed scope the shared work runs on.
 */
class RequestCoalescer<K : Any>(
    private val scope: CoroutineScope,
) {

    private val mutex = Mutex()
    private val inFlight = mutableMapOf<K, Deferred<*>>()

    /**
     * Runs [block] for [key], or — if an identical call is already in flight — joins it and awaits
     * the same result. The entry is removed once the shared call settles, so a later [join] with the
     * same key starts a fresh call.
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T> join(key: K, block: suspend () -> T): T {
        val deferred: Deferred<T> = mutex.withLock {
            (inFlight[key] as Deferred<T>?)
                ?: scope.async {
                    try {
                        block()
                    } finally {
                        mutex.withLock { inFlight.remove(key) }
                    }
                }.also { inFlight[key] = it }
        }
        return deferred.await()
    }
}
