package pm.bam.gamedeals.domain.repositories.releases

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import pm.bam.gamedeals.common.onError
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.db.dao.ReleasesDao
import pm.bam.gamedeals.domain.models.Release
import pm.bam.gamedeals.domain.repositories.cache.CachedResource
import pm.bam.gamedeals.domain.source.IgdbSource
import pm.bam.gamedeals.domain.utils.millisInDay
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.debug
import pm.bam.gamedeals.logging.fatal

/** New releases are a slow-moving feed (24-hour tier — ITAD caching strategy, Phase 1). */
internal val RELEASES_TTL_MILLIS = millisInDay

interface ReleasesRepository {
    fun observeReleases(): Flow<List<Release>>
    suspend fun refreshReleases()
}

internal class ReleasesRepositoryImpl(
    private val logger: Logger,
    private val releasesDao: ReleasesDao,
    private val igdbSource: IgdbSource,
    private val clock: Clock,
) : ReleasesRepository {

    private val cache = CachedResource(
        clock = clock,
        read = { releasesDao.getAllReleases() },
        expiresAtMillis = { it.expires },
        refresh = {
            val expiresAt = clock.nowMillis() + RELEASES_TTL_MILLIS
            releasesDao.replaceAll(igdbSource.fetchNewReleases().map { it.copy(expires = expiresAt) })
        }
    )

    override fun observeReleases(): Flow<List<Release>> =
        releasesDao.observeAllReleases()
            .onStart { refreshReleases() }
            .onError { fatal(logger, it) }

    override suspend fun refreshReleases() {
        val refreshed = cache.refreshIfNeeded()
        debug(logger) { "Releases refresh needed: $refreshed" }
    }
}
