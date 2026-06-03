package pm.bam.gamedeals.remote.cheapshark

import com.skydoves.sandwich.getOrThrow
import kotlinx.collections.immutable.persistentListOf
import pm.bam.gamedeals.common.datetime.formatting.DateTimeFormatter
import pm.bam.gamedeals.domain.models.Bundle
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.DealDetails
import pm.bam.gamedeals.domain.models.Game
import pm.bam.gamedeals.domain.models.GameDetails
import pm.bam.gamedeals.domain.models.PriceHistory
import pm.bam.gamedeals.domain.models.SearchParameters
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.source.DealsSource
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.remote.cheapshark.api.DealsApi
import pm.bam.gamedeals.remote.cheapshark.api.GamesApi
import pm.bam.gamedeals.remote.cheapshark.api.StoresApi
import pm.bam.gamedeals.remote.cheapshark.api.models.deals.RemoteDealsQuery
import pm.bam.gamedeals.remote.cheapshark.mappers.toDeal
import pm.bam.gamedeals.remote.cheapshark.mappers.toDealDetails
import pm.bam.gamedeals.remote.cheapshark.mappers.toGame
import pm.bam.gamedeals.remote.cheapshark.mappers.toGameDetails
import pm.bam.gamedeals.remote.cheapshark.mappers.toRemoteDealsQuery
import pm.bam.gamedeals.remote.cheapshark.mappers.toStore
import pm.bam.gamedeals.remote.cheapshark.transformations.CurrencyTransformation
import pm.bam.gamedeals.remote.exceptions.RemoteExceptionTransformer
import pm.bam.gamedeals.remote.logic.log
import pm.bam.gamedeals.remote.logic.mapAnyFailure

internal class CheapsharkSourceImpl(
    private val logger: Logger,
    private val dealsApi: DealsApi,
    private val gamesApi: GamesApi,
    private val storesApi: StoresApi,
    private val remoteExceptionTransformer: RemoteExceptionTransformer,
    private val currencyTransformation: CurrencyTransformation,
    private val datetimeFormatter: DateTimeFormatter
) : DealsSource {

    override suspend fun fetchDealsForStore(query: SearchParameters?): List<Deal> =
        dealsApi.getDeals(query?.toRemoteDealsQuery() ?: RemoteDealsQuery())
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
            .map { it.toDeal(currencyTransformation) }

    override suspend fun fetchDealDetails(id: String): DealDetails =
        dealsApi.getDeal(id)
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
            .toDealDetails(currencyTransformation, datetimeFormatter)

    override suspend fun fetchGames(
        title: String,
        steamAppID: Int?,
        limit: Int?,
        pageNumber: Int?
    ): List<Game> =
        gamesApi.getGames(title, steamAppID, limit, pageNumber)
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
            .map { it.toGame(currencyTransformation) }

    override suspend fun fetchGameDetails(id: String): GameDetails =
        gamesApi.getGame(id)
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
            .toGameDetails(currencyTransformation, datetimeFormatter)

    // CheapShark exposes only a single cheapest-ever price+date, not a full time series, so it cannot
    // back the price-history chart (#208). It is no longer the live source (ITAD is, since Phase 2b) and
    // is removed in Phase 4; return an empty series rather than throwing if this dead path is ever hit.
    override suspend fun fetchPriceHistory(gameId: String): PriceHistory =
        PriceHistory(gameID = gameId, points = persistentListOf())

    // CheapShark has no bundles endpoint (ITAD-only capability). Dead path (removed in Phase 4).
    override suspend fun fetchBundles(): List<Bundle> = emptyList()

    override suspend fun fetchStores(): List<Store> =
        storesApi.getStores()
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
            .map { it.toStore() }

    private companion object {
        private val TAG: String = CheapsharkSourceImpl::class.simpleName.orEmpty()
    }
}
