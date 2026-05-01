package pm.bam.gamedeals.domain.repositories.cache

import pm.bam.gamedeals.common.time.Clock

/**
 * A small, deeply-tested abstraction over the project's repeated
 * "if cache is empty or any entry is expired, clear-and-fetch under a transaction" pattern.
 *
 * Each repository wires one [CachedResource] per logical resource (e.g. "store deals for storeId",
 * "all stores"). The repository tells the cache how to read existing entries, when an entry counts
 * as expired, and how to perform the refresh. The cache decides — based solely on the injected
 * [Clock] and the values returned by [read] — whether [refresh] should run.
 *
 * The cache is intentionally Flow-agnostic and free of Room types. The repository is responsible
 * for running [refresh] inside `DomainDatabase.withTransaction { ... }` (where applicable), so this
 * class stays a pure-Kotlin unit-testable seam without an Android dependency.
 *
 * @param clock Time source used to compare an entry's expiry to "now". The single production
 *   `System.currentTimeMillis()` call lives in the [Clock] adapter.
 * @param read Returns the currently-cached entries. An empty result is treated as "needs refresh".
 * @param expiresAtMillis Returns the epoch-millisecond expiry of a single entry. The cache is
 *   considered stale when [Clock.nowMillis] is strictly greater than this value for any entry —
 *   matching the original `expires < System.currentTimeMillis()` semantics.
 * @param refresh The repository's "clear + fetch + insert" body. Invoked when the cache decides a
 *   refresh is required (or when the caller passes `force = true`).
 */
class CachedResource<T>(
    private val clock: Clock,
    private val read: suspend () -> List<T>,
    private val expiresAtMillis: (T) -> Long,
    private val refresh: suspend () -> Unit,
) {

    /**
     * Refreshes the underlying cache when needed.
     *
     * @param force When `true`, skips the freshness check and always invokes [refresh].
     * @return `true` when [refresh] was invoked, `false` when the cache was considered fresh.
     */
    suspend fun refreshIfNeeded(force: Boolean = false): Boolean {
        val needed = force || isStale()
        if (needed) refresh()
        return needed
    }

    private suspend fun isStale(): Boolean {
        val cached = read()
        if (cached.isEmpty()) return true
        val now = clock.nowMillis()
        return cached.any { expiresAtMillis(it) < now }
    }
}
