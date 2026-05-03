package pm.bam.gamedeals.remote.cheapshark

import android.util.Log
import com.skydoves.sandwich.getOrThrow
import pm.bam.gamedeals.common.datetime.formatting.DateTimeFormatter
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
import pm.bam.gamedeals.remote.cheapshark.mappers.toDeal
import pm.bam.gamedeals.remote.cheapshark.mappers.toDealDetails
import pm.bam.gamedeals.remote.cheapshark.mappers.toGame
import pm.bam.gamedeals.remote.cheapshark.mappers.toGameDetails
import pm.bam.gamedeals.remote.cheapshark.mappers.toRelease
import pm.bam.gamedeals.remote.cheapshark.mappers.toRemoteDealsQuery
import pm.bam.gamedeals.remote.cheapshark.mappers.toStore
import pm.bam.gamedeals.remote.cheapshark.transformations.CurrencyTransformation
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
    private val currencyTransformation: CurrencyTransformation,
    private val datetimeFormatter: DateTimeFormatter
) : CheapsharkSource {

    override suspend fun fetchDealsForStore(query: SearchParameters?): List<Deal> {
        val remoteQuery = query?.toRemoteDealsQuery()
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
        )
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
            .map { it.toDeal(currencyTransformation) }
    }

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

    override suspend fun fetchReleases(): List<Release> =
        releaseApi.getReleases()
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
            .map { it.toRelease() }

    override suspend fun fetchStores(): List<Store> {
        // TEMP DEBUG (phase-3 silent-failure investigation): bracket the suspend call
        // to discriminate hang ("calling getStores" with no follow-up) vs cancellation
        // vs exception. Remove once cause is found. Lives in androidMain so Log.d
        // surfaces under the existing `Ktor` Logcat filter the user is using.
        Log.d("Ktor", "fetchStores: calling storesApi.getStores")
        val response = try {
            storesApi.getStores()
        } catch (t: Throwable) {
            Log.d("Ktor", "fetchStores: storesApi.getStores threw ${t::class.simpleName}: ${t.message}")
            throw t
        }
        Log.d("Ktor", "fetchStores: got ApiResponse: $response")
        return response
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
            .map { it.toStore() }
            .also { Log.d("Ktor", "fetchStores: unwrapped ${it.size} stores") }
    }

    private companion object {
        private val TAG: String = CheapsharkSourceImpl::class.simpleName.orEmpty()
    }
}
