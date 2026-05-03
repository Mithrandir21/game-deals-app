package pm.bam.gamedeals.domain.repositories.releases

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import pm.bam.gamedeals.common.onError
import pm.bam.gamedeals.domain.db.dao.ReleasesDao
import pm.bam.gamedeals.domain.models.Release
import pm.bam.gamedeals.domain.source.CheapsharkSource
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.fatal

class ReleasesRepository internal constructor(
    private val logger: Logger,
    private val releasesDao: ReleasesDao,
    private val cheapsharkSource: CheapsharkSource
) {

    fun observeReleases(): Flow<List<Release>> =
        releasesDao.observeAllReleases()
            .onStart { refreshReleases() }
            .onError { fatal(logger, it) }

    suspend fun refreshReleases() =
        cheapsharkSource.fetchReleases()
            .let { releasesDao.addReleases(*it.toTypedArray()) }
}
