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
import pm.bam.gamedeals.remote.itad.mappers.isGameLikeProductType
import pm.bam.gamedeals.remote.itad.mappers.toItadGamePrices
import pm.bam.gamedeals.remote.itad.mappers.toRankedGame
import pm.bam.gamedeals.remote.itad.models.RemoteItadGameInfo
import pm.bam.gamedeals.remote.itad.models.RemoteItadRankedGame
import pm.bam.gamedeals.remote.itad.models.toGameArtwork
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
        val pricesDeferred = async { pricesByGameId(gameIds) }
        val infosDeferred = async { infoByGameId(gameIds) }

        val prices = pricesDeferred.await()
        val infos = infosDeferred.await()

        ranked
            // The ranking endpoints carry no `type`, but the per-game info (already fetched for boxart) does
            // — and ITAD leaves software/hardware with a null type, so keep only game-like products. Guard
            // the best-effort fetch: a game whose info call *failed* (no map entry) is kept rather than risk
            // hiding a real game on a transient error; only a *successful* non-game-like info drops it. The
            // row may then show slightly fewer than `limit`.
            .filter { ranked -> infos[ranked.gameId]?.let { it.type.isGameLikeProductType() } ?: true }
            .map { game ->
                game.copy(
                    priceDenominated = prices[game.gameId],
                    artwork = infos[game.gameId]?.assets.toGameArtwork(),
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

    /**
     * Full game info per game (best-effort: a failure yields no entry, so its type/boxart are unknown).
     * One `/games/info/v2` call per ranked game backs both the boxart enrichment and the software/hardware
     * type filter, so the filter adds no extra network cost.
     */
    private suspend fun infoByGameId(gameIds: List<String>): Map<String, RemoteItadGameInfo> = coroutineScope {
        gameIds.map { id ->
            async {
                runCatching {
                    id to gamesApi.getInfo(id)
                        .log(logger, tag = TAG)
                        .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
                        .getOrThrow()
                }.getOrNull()
            }
        }.awaitAll().filterNotNull().toMap()
    }

    private companion object {
        private val TAG: String = ItadStatsSourceImpl::class.simpleName.orEmpty()
    }
}
