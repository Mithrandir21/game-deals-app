package pm.bam.gamedeals.domain.repositories.signals

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.models.GameSignals
import pm.bam.gamedeals.domain.source.GameSignalsSource
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.featureflags.FeatureFlag
import pm.bam.gamedeals.logging.featureflags.FeatureFlags
import pm.bam.gamedeals.logging.warn

/**
 * Public per-game signals (sale history, reviews, players, ITAD counts) for list rows, from the Game Deals Worker.
 * The Worker refreshes each game from ITAD once a day, so this costs the shared ITAD allowance nothing per phone.
 * With the [FeatureFlag.GameSignals] switch off there are none.
 */
interface GameSignalsRepository {
    /**
     * Signals for whichever of [gameIds] the Worker has: from memory when fetched in the last [FRESH_FOR_MS], the
     * rest in batched calls. Best-effort: games the Worker is still fetching, or a failed call, are simply absent
     * (an older copy is used when there is one). Ids that aren't ITAD game ids are ignored.
     */
    suspend fun getSignals(gameIds: Collection<String>): Map<String, GameSignals>

    companion object {
        const val FRESH_FOR_MS: Long = 6 * 60 * 60 * 1000
    }
}

internal class GameSignalsRepositoryImpl(
    private val source: GameSignalsSource,
    private val clock: Clock,
    private val logger: Logger,
    private val featureFlags: FeatureFlags,
) : GameSignalsRepository {

    private class Cached(val signals: GameSignals, val fetchedAt: Long)

    private val mutex = Mutex()
    private val cache = mutableMapOf<String, Cached>()

    // Games the Worker said it is still fetching: not asked about again until this time.
    private val pendingUntil = mutableMapOf<String, Long>()

    override suspend fun getSignals(gameIds: Collection<String>): Map<String, GameSignals> {
        // Switched off: no Worker calls, and what is already in memory isn't shown either.
        if (!featureFlags.isEnabled(FeatureFlag.GameSignals)) return emptyMap()
        val ids = gameIds.filter { ITAD_ID.matches(it) }.distinct()
        if (ids.isEmpty()) return emptyMap()
        val now = clock.nowMillis()
        val result = mutableMapOf<String, GameSignals>()
        val toFetch = mutex.withLock {
            ids.filter { id ->
                val cached = cache[id]
                cached?.let { result[id] = it.signals }
                val fresh = cached != null && now - cached.fetchedAt < GameSignalsRepository.FRESH_FOR_MS
                !fresh && (pendingUntil[id] ?: 0L) <= now
            }
        }
        for (chunk in toFetch.chunked(GameSignalsSource.MAX_IDS)) {
            val batch = try {
                source.fetch(chunk)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                warn(logger, error) { "Game signals couldn't be loaded for ${chunk.size} games" }
                continue
            }
            mutex.withLock {
                batch.games.forEach { signals ->
                    cache[signals.gameId] = Cached(signals, now)
                    pendingUntil.remove(signals.gameId)
                    if (signals.gameId in chunk) result[signals.gameId] = signals
                }
                batch.pending.forEach { pendingUntil[it] = now + PENDING_RETRY_MS }
                trim()
            }
        }
        return result
    }

    /** Keeps memory bounded: drops the oldest entries once there are more than [MAX_CACHED]. */
    private fun trim() {
        if (cache.size <= MAX_CACHED) return
        cache.entries.sortedBy { it.value.fetchedAt }.take(cache.size - MAX_CACHED + MAX_CACHED / 4)
            .forEach { cache.remove(it.key) }
        pendingUntil.entries.removeAll { it.value <= clock.nowMillis() }
    }

    private companion object {
        /** The Worker fetches pending games in the background; this is roughly how long that takes to be worth asking. */
        const val PENDING_RETRY_MS: Long = 60 * 1000
        const val MAX_CACHED = 2_000
        val ITAD_ID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
    }
}
