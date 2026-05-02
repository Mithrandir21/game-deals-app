package pm.bam.gamedeals.remote.cheapshark

import com.skydoves.sandwich.ApiResponse
import com.skydoves.sandwich.getOrThrow
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.DealDetails
import pm.bam.gamedeals.domain.models.Game
import pm.bam.gamedeals.domain.models.GameDetails
import pm.bam.gamedeals.domain.models.Release
import pm.bam.gamedeals.domain.models.SearchParameters
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.source.CheapsharkSource
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.remote.cheapshark.api.DealsApi
import pm.bam.gamedeals.remote.cheapshark.api.GamesApi
import pm.bam.gamedeals.remote.cheapshark.api.ReleaseApi
import pm.bam.gamedeals.remote.cheapshark.api.StoresApi
import pm.bam.gamedeals.remote.cheapshark.mappers.CheapsharkMapperContext
import pm.bam.gamedeals.remote.cheapshark.mappers.toDomain
import pm.bam.gamedeals.remote.exceptions.RemoteExceptionTransformer
import pm.bam.gamedeals.remote.logic.log
import pm.bam.gamedeals.remote.logic.mapAnyFailure
import javax.inject.Inject

internal class CheapsharkSourceImpl @Inject constructor(
    private val logger: Logger,
    private val dealsApi: DealsApi,
    private val gamesApi: GamesApi,
    private val releaseApi: ReleaseApi,
    private val storesApi: StoresApi,
    private val remoteExceptionTransformer: RemoteExceptionTransformer,
    private val ctx: CheapsharkMapperContext,
) : CheapsharkSource {

    override suspend fun fetchDealsForStore(query: SearchParameters?): List<Deal> {
        val remoteQuery = query?.toDomain()
        return dealsApi.getDeals(
            storeID = remoteQuery?.storeID,
            pageNumber = remoteQuery?.pageNumber,
            pageSize = remoteQuery?.pageSize,
            sortBy = remoteQuery?.sortBy,
            desc = remoteQuery?.desc,
            lowerPrice = remoteQuery?.lowerPrice,
            upperPrice = remoteQuery?.upperPrice,
            metacritic = remoteQuery?.metacritic,
            steamRating = remoteQuery?.steamRating,
            maxAge = remoteQuery?.maxAge,
            steamAppID = remoteQuery?.steamAppID,
            title = remoteQuery?.title,
            exact = remoteQuery?.exact,
            aaa = remoteQuery?.aaa,
            steamworks = remoteQuery?.steamworks,
            onSale = remoteQuery?.onSale
        ).unwrap().map { it.toDomain(ctx) }
    }

    override suspend fun fetchDealDetails(id: String): DealDetails =
        dealsApi.getDeal(id).unwrap().toDomain(ctx)

    override suspend fun fetchGames(
        title: String,
        steamAppID: Int?,
        limit: Int?,
        pageNumber: Int?
    ): List<Game> =
        gamesApi.getGames(title, steamAppID, limit, pageNumber)
            .unwrap()
            .map { it.toDomain(ctx) }

    override suspend fun fetchGameDetails(id: String): GameDetails =
        gamesApi.getGame(id).unwrap().toDomain(ctx)

    override suspend fun fetchReleases(): List<Release> =
        releaseApi.getReleases().unwrap().map { it.toDomain() }

    override suspend fun fetchStores(): List<Store> =
        storesApi.getStores().unwrap().map { it.toDomain() }

    private fun <T> ApiResponse<T>.unwrap(): T =
        log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()

    private companion object {
        private val TAG: String = CheapsharkSourceImpl::class.simpleName.orEmpty()
    }
}
