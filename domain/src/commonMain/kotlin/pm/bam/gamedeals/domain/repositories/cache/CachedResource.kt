package pm.bam.gamedeals.domain.repositories.cache

import pm.bam.gamedeals.common.time.Clock

class CachedResource<T>(
    private val clock: Clock,
    private val read: suspend () -> List<T>,
    private val expiresAtMillis: (T) -> Long,
    private val refresh: suspend () -> Unit,
) {

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
