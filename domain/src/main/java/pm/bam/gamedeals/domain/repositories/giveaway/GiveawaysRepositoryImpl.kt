package pm.bam.gamedeals.domain.repositories.giveaway

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pm.bam.gamedeals.common.datetime.parsing.DatetimeParsing
import pm.bam.gamedeals.common.onError
import pm.bam.gamedeals.domain.db.dao.GiveawaysDao
import pm.bam.gamedeals.domain.models.Giveaway
import pm.bam.gamedeals.domain.models.GiveawaySearchParameters
import pm.bam.gamedeals.domain.models.GiveawaySortBy
import pm.bam.gamedeals.domain.models.toGiveaway
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.fatal
import pm.bam.gamedeals.remote.gamerpower.GamerPowerSource
import javax.inject.Inject

internal class GiveawaysRepositoryImpl @Inject constructor(
    private val logger: Logger,
    private val giveawaysDao: GiveawaysDao,
    private val gamerPowerSource: GamerPowerSource,
    private val datetimeParsing: DatetimeParsing
) : GiveawaysRepository {

    override fun observeGiveaways(): Flow<List<Giveaway>> =
        giveawaysDao.observeAllGiveaways()
            .map { items -> items.sortedByDescending { it.publishedDate } }
            .onError { fatal(logger, it) }

    override fun observeGiveaways(giveawaySearchParameters: GiveawaySearchParameters): Flow<List<Giveaway>> {
        val typeValues = giveawaySearchParameters.types
            .filter { it.second }
            .map { it.first }

        val requestedPlatforms = giveawaySearchParameters.platforms
            .filter { it.second }
            .map { it.first }

        return giveawaysDao.observeAllGiveaways()
            .map { items ->
                if (requestedPlatforms.isEmpty()) return@map items
                else items.filter { giveaway -> giveaway.platforms.any { requestedPlatforms.contains(it) } }
            }
            .map { items ->
                if (typeValues.isEmpty()) return@map items
                else items.filter { giveaway -> typeValues.contains(giveaway.type) }
            }
            .map { items ->
                when (giveawaySearchParameters.sortBy) {
                    GiveawaySortBy.DATE -> items.sortedByDescending { it.publishedDate }
                    GiveawaySortBy.POPULARITY -> items.sortedByDescending { it.users }
                    GiveawaySortBy.VALUE -> items.sortedByDescending { it.worth }
                }
            }
            .onError { fatal(logger, it) }
    }

    override suspend fun refreshGiveaways() =
        gamerPowerSource.fetchGiveaways()
            .map { remoteRelease -> remoteRelease.toGiveaway(datetimeParsing) }
            .let { giveawaysDao.addGiveaways(*it.toTypedArray()) }
}
