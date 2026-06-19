package pm.bam.gamedeals.domain.repositories.pricewatch

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.builtins.ListSerializer
import pm.bam.gamedeals.common.storage.Storage
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.models.PriceWatch
import pm.bam.gamedeals.domain.repositories.region.RegionRepository

/**
 * The user's local target-price alerts (Phase 3), persisted via [Storage] (same SharedPreferences /
 * NSUserDefaults store as [SettingsRepository][pm.bam.gamedeals.domain.repositories.settings.SettingsRepository]).
 * Keyed by `gameId` (one watch per game). Exposed reactively so the game page reflects the current watch the
 * moment it's set or cleared, mirroring the lazily-seeded `StateFlow` pattern used across the repos.
 */
interface PriceWatchRepository {
    fun observeWatches(): Flow<List<PriceWatch>>
    fun observeWatch(gameId: String): Flow<PriceWatch?>
    suspend fun getWatches(): List<PriceWatch>
    suspend fun getWatch(gameId: String): PriceWatch?

    /**
     * Sets (or replaces) the target-price alert for [gameId]. The region + creation timestamp are filled in
     * here; setting a new target always resets the fired-dedupe so the next crossing alerts.
     */
    suspend fun setWatch(gameId: String, title: String, targetPriceValue: Double, targetPriceDenominated: String)
    suspend fun removeWatch(gameId: String)

    /** Records the price at which [gameId] last fired (or `null` to reset) — the background-check dedupe. */
    suspend fun markNotified(gameId: String, lastNotifiedPriceValue: Double?)
}

internal const val PRICE_WATCHES_KEY = "price_watches"

internal class PriceWatchRepositoryImpl(
    private val storage: Storage,
    private val regionRepository: RegionRepository,
    private val clock: Clock,
) : PriceWatchRepository {

    // Reactive source of truth, lazily seeded from [storage] on first access (null = not yet loaded).
    private val watches = MutableStateFlow<List<PriceWatch>?>(null)

    override fun observeWatches(): Flow<List<PriceWatch>> =
        watches
            .onStart { if (watches.value == null) watches.value = load() }
            .filterNotNull()

    override fun observeWatch(gameId: String): Flow<PriceWatch?> =
        observeWatches().map { list -> list.firstOrNull { it.gameId == gameId } }

    override suspend fun getWatches(): List<PriceWatch> {
        if (watches.value == null) watches.value = load()
        return watches.value.orEmpty()
    }

    override suspend fun getWatch(gameId: String): PriceWatch? =
        getWatches().firstOrNull { it.gameId == gameId }

    override suspend fun setWatch(gameId: String, title: String, targetPriceValue: Double, targetPriceDenominated: String) {
        val watch = PriceWatch(
            gameId = gameId,
            title = title,
            targetPriceValue = targetPriceValue,
            targetPriceDenominated = targetPriceDenominated,
            country = regionRepository.getSelectedCountryCode(),
            createdAtMs = clock.nowMillis(),
            lastNotifiedPriceValue = null,
        )
        persist(getWatches().filterNot { it.gameId == gameId } + watch)
    }

    override suspend fun removeWatch(gameId: String) =
        persist(getWatches().filterNot { it.gameId == gameId })

    override suspend fun markNotified(gameId: String, lastNotifiedPriceValue: Double?) {
        val current = getWatches()
        val updated = current.map { if (it.gameId == gameId) it.copy(lastNotifiedPriceValue = lastNotifiedPriceValue) else it }
        if (updated != current) persist(updated)
    }

    private suspend fun persist(list: List<PriceWatch>) {
        storage.save(PRICE_WATCHES_KEY, list, ListSerializer(PriceWatch.serializer()))
        watches.value = list
    }

    private suspend fun load(): List<PriceWatch> =
        runCatching { storage.getNullable(PRICE_WATCHES_KEY, ListSerializer(PriceWatch.serializer())) }.getOrNull() ?: emptyList()
}
