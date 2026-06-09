package pm.bam.gamedeals.remote.itad

import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.getOrThrow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import pm.bam.gamedeals.domain.models.RankedGame
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
import pm.bam.gamedeals.domain.source.StatsSource
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.remote.exceptions.RemoteExceptionTransformer
import pm.bam.gamedeals.remote.itad.api.ItadGamesApi
import pm.bam.gamedeals.remote.itad.api.ItadStatsApi
import pm.bam.gamedeals.remote.itad.mappers.denominated
import pm.bam.gamedeals.remote.itad.mappers.toItadGamePrices
import pm.bam.gamedeals.remote.itad.mappers.toRankedGame
import pm.bam.gamedeals.remote.itad.models.RemoteItadRankedGame
import pm.bam.gamedeals.remote.itad.models.bestArt
import pm.bam.gamedeals.remote.logic.log
import pm.bam.gamedeals.remote.logic.mapAnyFailure

/**
 * IsThereAnyDeal implementation of [StatsSource] (epic #219, Phase 5.1) — the global ranking endpoints
 * (`/stats/most-waitlisted|most-collected|most-popular/v1`), which are API-key only.
 *
 * The ranking endpoints carry only ids + titles, so each [RankedGame] is enriched with its current best
 * price via a single batched `POST /games/prices/v3` (region-aware). Price enrichment is **best-effort**:
 * a prices failure leaves the rankings without a price rather than sinking the section.
 */
internal class ItadStatsSourceImpl(
    private val logger: Logger,
    private val statsApi: ItadStatsApi,
    private val gamesApi: ItadGamesApi,
    private val remoteExceptionTransformer: RemoteExceptionTransformer,
    private val regionRepository: RegionRepository,
) : StatsSource {

    override suspend fun fetchMostWaitlisted(limit: Int?): List<RankedGame> =
        enrich(statsApi.getMostWaitlisted(limit = limit))

    override suspend fun fetchMostCollected(limit: Int?): List<RankedGame> =
        enrich(statsApi.getMostCollected(limit = limit))

    override suspend fun fetchMostPopular(limit: Int?): List<RankedGame> =
        enrich(statsApi.getMostPopular(limit = limit))

    private suspend fun enrich(response: ApiResponse<List<RemoteItadRankedGame>>): List<RankedGame> = coroutineScope {
        val ranked = response
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
            .map { it.toRankedGame() }
        if (ranked.isEmpty()) return@coroutineScope ranked

        val gameIds = ranked.map { it.gameId }
        val priceByGameId = async { pricesByGameId(gameIds) }
        val boxartByGameId = async { boxartsByGameId(gameIds) }

        val prices = priceByGameId.await()
        val boxarts = boxartByGameId.await()

        ranked.map { game ->
            game.copy(
                priceDenominated = prices[game.gameId],
                boxart = boxarts[game.gameId]
            )
        }
    }

    /** Cheapest current price per game (best-effort: a failure yields an empty map, so prices are omitted). */
    private suspend fun pricesByGameId(gameIds: List<String>): Map<String, String> = runCatching {
        gamesApi.getPrices(gameIds = gameIds, country = regionRepository.getSelectedCountryCode())
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
            .map { it.toItadGamePrices() }
            .mapNotNull { prices -> prices.deals.minByOrNull { it.price.amount }?.let { prices.gameId to it.price.denominated() } }
            .toMap()
    }.getOrElse { emptyMap() }

    /** Game boxart assets per game (best-effort: a failure yields an empty map, so assets are omitted). */
    private suspend fun boxartsByGameId(gameIds: List<String>): Map<String, String> = coroutineScope {
        gameIds.map { id ->
            async {
                runCatching {
                    gamesApi.getInfo(id)
                        .log(logger, tag = TAG)
                        .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
                        .getOrThrow()
                        .assets.bestArt()?.let { art -> id to art }
                }.getOrNull()
            }
        }.awaitAll().filterNotNull().toMap()
    }

    private companion object {
        private val TAG: String = ItadStatsSourceImpl::class.simpleName.orEmpty()
    }
}
