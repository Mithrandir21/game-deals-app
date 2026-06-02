package pm.bam.gamedeals.remote.itad

import com.skydoves.sandwich.getOrThrow
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.DealDetails
import pm.bam.gamedeals.domain.models.Game
import pm.bam.gamedeals.domain.models.GameDetails
import pm.bam.gamedeals.domain.models.SearchParameters
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.source.DealsSource
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.remote.exceptions.RemoteExceptionTransformer
import pm.bam.gamedeals.remote.itad.api.ItadDealsApi
import pm.bam.gamedeals.remote.itad.api.ItadGamesApi
import pm.bam.gamedeals.remote.itad.api.ItadShopsApi
import pm.bam.gamedeals.remote.itad.mappers.toItadDeals
import pm.bam.gamedeals.remote.itad.mappers.toItadGamePrices
import pm.bam.gamedeals.remote.itad.mappers.toItadGameSearchResult
import pm.bam.gamedeals.remote.itad.mappers.toItadPriceHistoryEntry
import pm.bam.gamedeals.remote.itad.mappers.toStore
import pm.bam.gamedeals.remote.itad.models.ItadDeal
import pm.bam.gamedeals.remote.itad.models.ItadGamePrices
import pm.bam.gamedeals.remote.itad.models.ItadGameSearchResult
import pm.bam.gamedeals.remote.itad.models.ItadPriceHistoryEntry
import pm.bam.gamedeals.remote.logic.log
import pm.bam.gamedeals.remote.logic.mapAnyFailure

/**
 * IsThereAnyDeal implementation of the source-neutral [DealsSource] port (epic #205).
 *
 * Phase 1 scope: this stands up the ITAD module (client, DTOs, mappers) and proves the fetch + map
 * path. ITAD identifies games by UUID and exposes a shape that doesn't fit the app's current
 * CheapShark-shaped domain models (`Deal.gameID: Int`, steam-rating fields, …), and the app's
 * domain models only migrate to UUIDs in Phase 2 — so the methods that would return those models
 * throw [UnsupportedOperationException] for now. [fetchStores] is honoured because ITAD shop ids are
 * already integers and map straight onto [Store].
 *
 * The ITAD-shaped façade ([fetchDeals], [fetchGamePrices], [fetchPriceHistory], [searchGames],
 * [lookupBySteamAppId]) carries the real Phase 1 capability and is exercised by the module's tests;
 * Phase 2/3 bridge it into the migrated domain and surface it through the seam/UI.
 */
internal class ItadSourceImpl(
    private val logger: Logger,
    private val shopsApi: ItadShopsApi,
    private val dealsApi: ItadDealsApi,
    private val gamesApi: ItadGamesApi,
    private val remoteExceptionTransformer: RemoteExceptionTransformer,
) : DealsSource {

    // --- DealsSource (implemented) ---

    override suspend fun fetchStores(): List<Store> =
        shopsApi.getShops()
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
            .map { it.toStore() }

    // --- DealsSource (deferred to #205 Phase 2: needs the UUID/Room domain-model migration) ---

    override suspend fun fetchDealsForStore(query: SearchParameters?): List<Deal> = throw notYetSupported("fetchDealsForStore")

    override suspend fun fetchDealDetails(id: String): DealDetails = throw notYetSupported("fetchDealDetails")

    override suspend fun fetchGames(title: String, steamAppID: Int?, limit: Int?, pageNumber: Int?): List<Game> =
        throw notYetSupported("fetchGames")

    override suspend fun fetchGameDetails(id: String): GameDetails = throw notYetSupported("fetchGameDetails")

    // --- ITAD-shaped façade (Phase 1 fetch + map; surfaced via the seam in Phase 2/3) ---

    suspend fun fetchDeals(
        country: String? = null,
        offset: Int? = null,
        limit: Int? = null,
        sort: String? = null,
        shops: String? = null,
    ): List<ItadDeal> =
        dealsApi.getDeals(country = country, offset = offset, limit = limit, sort = sort, shops = shops)
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
            .flatMap { it.toItadDeals() }

    suspend fun fetchGamePrices(gameIds: List<String>, country: String? = null): List<ItadGamePrices> =
        gamesApi.getPrices(gameIds = gameIds, country = country)
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
            .map { it.toItadGamePrices() }

    suspend fun fetchPriceHistory(gameId: String, country: String? = null): List<ItadPriceHistoryEntry> =
        gamesApi.getHistory(gameId = gameId, country = country)
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
            .mapNotNull { it.toItadPriceHistoryEntry() }

    suspend fun searchGames(title: String, results: Int? = null): List<ItadGameSearchResult> =
        gamesApi.searchGames(title = title, results = results)
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
            .map { it.toItadGameSearchResult() }

    /** Steam-appID → ITAD game bridge; the returned result carries the queried [appid]. */
    suspend fun lookupBySteamAppId(appid: Int): ItadGameSearchResult? =
        gamesApi.lookupGame(appid = appid)
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
            .takeIf { it.found }
            ?.game
            ?.toItadGameSearchResult(steamAppId = appid)

    private fun notYetSupported(method: String): UnsupportedOperationException = UnsupportedOperationException(
        "ITAD $method is not implemented in Phase 1 — see #205 Phase 2 (UUID/Room domain-model migration; releases → IGDB)."
    )

    private companion object {
        private val TAG: String = ItadSourceImpl::class.simpleName.orEmpty()
    }
}
