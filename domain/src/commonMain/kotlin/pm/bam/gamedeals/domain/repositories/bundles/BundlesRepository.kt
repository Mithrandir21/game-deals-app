package pm.bam.gamedeals.domain.repositories.bundles

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.db.cache.BundlesCacheEntry
import pm.bam.gamedeals.domain.db.dao.BundlesCacheDao
import pm.bam.gamedeals.domain.models.Bundle
import pm.bam.gamedeals.domain.models.BundleGamePrice
import pm.bam.gamedeals.domain.repositories.cache.CachedResource
import pm.bam.gamedeals.domain.repositories.cache.decodeOffMain
import pm.bam.gamedeals.domain.repositories.cache.encodeOffMain
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
import pm.bam.gamedeals.domain.source.DealsSource
import pm.bam.gamedeals.domain.utils.millisInHour

/** Bundles are a curated feed (12-hour tier — ITAD caching strategy §4 / Phase 5b). */
internal const val BUNDLES_TTL_MILLIS = millisInHour * 12

/**
 * Active storefront bundles (epic #205, Phase 3c). Region-keyed read-through cache (ITAD caching
 * strategy, Phase 5b): the region's whole `List<Bundle>` is cached as one blob at a 12h feed TTL and
 * [getBundle] resolves a single bundle from that cached list rather than refetching.
 */
interface BundlesRepository {
    suspend fun getBundles(): List<Bundle>
    suspend fun getBundle(id: Int): Bundle?

    /**
     * Current best price + all-time low for each of [gameIds] (the games in a bundle), in the user's
     * region — one batched query enriching the bundle detail screen. Best-effort and uncached (like
     * regional prices): a game with no current deal is omitted or carries null `best*` fields.
     */
    suspend fun getBundleGamePrices(gameIds: List<String>): List<BundleGamePrice>
}

internal class BundlesRepositoryImpl(
    private val dealsSource: DealsSource,
    private val clock: Clock,
    private val regionRepository: RegionRepository,
    private val bundlesCacheDao: BundlesCacheDao,
    private val json: Json,
) : BundlesRepository {

    private val serializer = ListSerializer(Bundle.serializer())

    /**
     * The region's bundles, cached per `(country)` at the 12h feed TTL. Read-through: cold fetches and
     * caches, fresh decodes the blob, stale refetches. Bounded by serve-stale-on-error (D7) — a warm
     * cache falls back to the cached list on a failed refresh; a cold cache surfaces the failure (the
     * caller treats the bundles section as best-effort).
     */
    override suspend fun getBundles(): List<Bundle> {
        val country = regionRepository.getSelectedCountryCode()
        val cachedEntry = bundlesCacheDao.get(country)
        var refreshed: List<Bundle>? = null
        val cache = CachedResource(
            clock = clock,
            read = { cachedEntry?.let(::listOf) ?: emptyList() },
            expiresAtMillis = { it.expires },
            refresh = {
                val fetched = dealsSource.fetchBundles()
                refreshed = fetched
                val now = clock.nowMillis()
                bundlesCacheDao.upsert(
                    BundlesCacheEntry(
                        country = country,
                        json = json.encodeOffMain(serializer, fetched),
                        fetchedAt = now,
                        expires = now + BUNDLES_TTL_MILLIS,
                    )
                )
            },
        )
        cache.refreshIfNeeded()
        return refreshed ?: cachedEntry?.let { json.decodeOffMain(serializer, it.json) } ?: emptyList()
    }

    override suspend fun getBundle(id: Int): Bundle? = getBundles().firstOrNull { it.id == id }

    override suspend fun getBundleGamePrices(gameIds: List<String>): List<BundleGamePrice> =
        dealsSource.fetchBundleGamePrices(gameIds)
}
