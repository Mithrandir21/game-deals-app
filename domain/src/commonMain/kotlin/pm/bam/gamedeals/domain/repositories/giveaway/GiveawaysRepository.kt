package pm.bam.gamedeals.domain.repositories.giveaway

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pm.bam.gamedeals.common.onError
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.db.dao.GiveawaysDao
import pm.bam.gamedeals.domain.models.Giveaway
import pm.bam.gamedeals.domain.models.GiveawaySearchParameters
import pm.bam.gamedeals.domain.models.GiveawaySortBy
import pm.bam.gamedeals.domain.repositories.cache.CachedResource
import pm.bam.gamedeals.domain.source.GamerPowerSource
import pm.bam.gamedeals.domain.utils.millisInHour
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.debug
import pm.bam.gamedeals.logging.fatal

/** Giveaways rotate intra-day (12-hour tier — ITAD caching strategy, Phase 1). */
internal const val GIVEAWAYS_TTL_MILLIS = millisInHour * 12

interface GiveawaysRepository {
    fun observeGiveaways(): Flow<List<Giveaway>>
    fun observeGiveaways(giveawaySearchParameters: GiveawaySearchParameters): Flow<List<Giveaway>>

    /** Resolves a single cached [Giveaway] by id for the detail screen, or `null` if it isn't cached. */
    suspend fun getGiveaway(id: Int): Giveaway?
    suspend fun refreshGiveaways()
}

internal class GiveawaysRepositoryImpl(
    private val logger: Logger,
    private val giveawaysDao: GiveawaysDao,
    private val gamerPowerSource: GamerPowerSource,
    private val clock: Clock,
) : GiveawaysRepository {

    private val cache = CachedResource(
        clock = clock,
        read = { giveawaysDao.getAllGiveaways() },
        expiresAtMillis = { it.expires },
        refresh = {
            val expiresAt = clock.nowMillis() + GIVEAWAYS_TTL_MILLIS
            giveawaysDao.replaceAll(gamerPowerSource.fetchGiveaways().map { it.copy(expires = expiresAt) })
        }
    )

    override fun observeGiveaways(): Flow<List<Giveaway>> =
        giveawaysDao.observeAllGiveaways()
            .map { items -> items.sortedByDescending { it.publishedDate } }
            .onError { fatal(logger, it) }

    override fun observeGiveaways(giveawaySearchParameters: GiveawaySearchParameters): Flow<List<Giveaway>> {
        val typeValues = giveawaySearchParameters.types
            .filter { it.selected }
            .map { it.type }

        val requestedPlatforms = giveawaySearchParameters.platforms
            .filter { it.selected }
            .map { it.platform }

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

    override suspend fun getGiveaway(id: Int): Giveaway? = giveawaysDao.getGiveaway(id)

    override suspend fun refreshGiveaways() {
        val refreshed = cache.refreshIfNeeded()
        debug(logger) { "Giveaways refresh needed: $refreshed" }
    }
}
