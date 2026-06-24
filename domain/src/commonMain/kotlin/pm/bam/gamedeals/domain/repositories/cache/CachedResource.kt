package pm.bam.gamedeals.domain.repositories.cache

import kotlinx.coroutines.CancellationException
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
     * **Serve-stale-on-error:** when [refresh] fails, the previously-cached entries are kept
     * (even if expired) rather than propagating the failure — so a flaky network or an ITAD
     * rate-limit degrades to last-known data instead of an error. The failure is only rethrown
     * when there is nothing to fall back to (the cache is empty) or when the caller opts in via
     * [surfaceErrors] (e.g. an explicit pull-to-refresh that wants to show a retry/snackbar while
     * still displaying the stale rows). [CancellationException] is always rethrown so structured
     * concurrency is preserved.
     *
     * @param force When `true`, skips the freshness check and always invokes [refresh].
     * @param surfaceErrors When `true`, a failed [refresh] is rethrown even if cached data exists.
     * @return `true` when [refresh] was invoked (whether or not it succeeded), `false` when the
     *   cache was considered fresh and no refresh ran.
     */
    suspend fun refreshIfNeeded(force: Boolean = false, surfaceErrors: Boolean = false): Boolean {
        val needed = force || isStale()
        if (!needed) return false
        try {
            refresh()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            if (surfaceErrors || read().isEmpty()) throw error
        }
        return true
    }

    private suspend fun isStale(): Boolean {
        val cached = read()
        if (cached.isEmpty()) return true
        val now = clock.nowMillis()
        return cached.any { expiresAtMillis(it) < now }
    }
}
