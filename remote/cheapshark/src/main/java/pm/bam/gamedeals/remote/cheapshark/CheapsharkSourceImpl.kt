package pm.bam.gamedeals.remote.cheapshark

import com.skydoves.sandwich.getOrThrow
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.remote.cheapshark.api.DealsApi
import pm.bam.gamedeals.remote.cheapshark.api.GamesApi
import pm.bam.gamedeals.remote.cheapshark.api.ReleaseApi
import pm.bam.gamedeals.remote.cheapshark.api.StoresApi
import pm.bam.gamedeals.remote.cheapshark.api.models.deals.RemoteDealsQuery
import pm.bam.gamedeals.remote.cheapshark.models.RemoteDeal
import pm.bam.gamedeals.remote.cheapshark.models.RemoteDealDetails
import pm.bam.gamedeals.remote.cheapshark.models.RemoteGame
import pm.bam.gamedeals.remote.cheapshark.models.RemoteGameDetails
import pm.bam.gamedeals.remote.cheapshark.models.RemoteRelease
import pm.bam.gamedeals.remote.cheapshark.models.RemoteStore
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
    private val remoteExceptionTransformer: RemoteExceptionTransformer
) : CheapsharkSource {

    override suspend fun fetchDealsForStore(query: RemoteDealsQuery?): List<RemoteDeal> =
        dealsApi.getDeals(
            storeID = query?.storeID,
            pageNumber = query?.pageNumber,
            pageSize = query?.pageSize,
            sortBy = query?.sortBy,
            desc = query?.desc,
            lowerPrice = query?.lowerPrice,
            upperPrice = query?.upperPrice,
            metacritic = query?.metacritic,
            steamRating = query?.steamRating,
            maxAge = query?.maxAge,
            steamAppID = query?.steamAppID,
            title = query?.title,
            exact = query?.exact,
            aaa = query?.aaa,
            steamworks = query?.steamworks,
            onSale = query?.onSale
        )
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()

    override suspend fun fetchDealDetails(id: String): RemoteDealDetails =
        dealsApi.getDeal(id)
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()

    override suspend fun fetchGames(
        title: String,
        steamAppID: Int?,
        limit: Int?,
        pageNumber: Int?
    ): List<RemoteGame> =
        gamesApi.getGames(title, steamAppID, limit, pageNumber)
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()

    override suspend fun fetchGameDetails(id: String): RemoteGameDetails =
        gamesApi.getGame(id)
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()

    override suspend fun fetchReleases(): List<RemoteRelease> =
        releaseApi.getReleases()
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()

    override suspend fun fetchStores(): List<RemoteStore> =
        storesApi.getStores()
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()

    private companion object {
        private val TAG: String = CheapsharkSourceImpl::class.simpleName.orEmpty()
    }
}
