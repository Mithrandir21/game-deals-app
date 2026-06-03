package pm.bam.gamedeals.domain.repositories.bundles

import pm.bam.gamedeals.domain.models.Bundle
import pm.bam.gamedeals.domain.source.DealsSource

/**
 * Active storefront bundles (epic #205, Phase 3c). Fetched fresh from the [DealsSource] — bundles are
 * not Room-cached (their nested tier/game structure makes caching costly), so [getBundle] re-resolves a
 * single bundle by id from the same source rather than reading a cache.
 */
interface BundlesRepository {
    suspend fun getBundles(): List<Bundle>
    suspend fun getBundle(id: Int): Bundle?
}

internal class BundlesRepositoryImpl(
    private val dealsSource: DealsSource,
) : BundlesRepository {

    override suspend fun getBundles(): List<Bundle> = dealsSource.fetchBundles()

    override suspend fun getBundle(id: Int): Bundle? = dealsSource.fetchBundles().firstOrNull { it.id == id }
}
