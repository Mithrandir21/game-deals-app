package pm.bam.gamedeals.remote.itad

import com.skydoves.sandwich.getOrThrow
import kotlin.time.Instant
import kotlinx.collections.immutable.persistentListOf
import pm.bam.gamedeals.domain.models.Bundle
import pm.bam.gamedeals.domain.models.Country
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.DealDetails
import pm.bam.gamedeals.domain.models.DealsQuery
import pm.bam.gamedeals.domain.models.Game
import pm.bam.gamedeals.domain.models.GameDetails
import pm.bam.gamedeals.domain.models.GameMeta
import pm.bam.gamedeals.domain.models.PriceHistory
import pm.bam.gamedeals.domain.models.RegionalPrice
import pm.bam.gamedeals.domain.models.SearchParameters
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
import pm.bam.gamedeals.domain.source.DealsSource
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.remote.exceptions.RemoteExceptionTransformer
import pm.bam.gamedeals.remote.itad.api.ItadBundlesApi
import pm.bam.gamedeals.remote.itad.api.ItadDealsApi
import pm.bam.gamedeals.remote.itad.api.ItadGamesApi
import pm.bam.gamedeals.remote.itad.api.ItadShopsApi
import pm.bam.gamedeals.remote.itad.mappers.denominated
import pm.bam.gamedeals.remote.itad.mappers.gameIdFromDealId
import pm.bam.gamedeals.remote.itad.mappers.toDeal
import pm.bam.gamedeals.remote.itad.mappers.toDealDetails
import pm.bam.gamedeals.remote.itad.mappers.toGame
import pm.bam.gamedeals.remote.itad.mappers.toGameDetails
import pm.bam.gamedeals.remote.itad.mappers.toGameMeta
import pm.bam.gamedeals.remote.itad.mappers.toItadDeal
import pm.bam.gamedeals.remote.itad.mappers.toItadGamePrices
import pm.bam.gamedeals.remote.itad.mappers.toItadGameSearchResult
import pm.bam.gamedeals.remote.itad.mappers.toBundle
import pm.bam.gamedeals.remote.itad.mappers.toItadPriceHistoryEntry
import pm.bam.gamedeals.remote.itad.mappers.toPriceHistory
import pm.bam.gamedeals.remote.itad.mappers.toStore
import pm.bam.gamedeals.remote.itad.models.ItadDeal
import pm.bam.gamedeals.remote.itad.models.ItadGamePrices
import pm.bam.gamedeals.remote.itad.models.ItadGameSearchResult
import pm.bam.gamedeals.remote.itad.models.ItadPriceHistoryEntry
import pm.bam.gamedeals.remote.logic.log
import pm.bam.gamedeals.remote.logic.mapAnyFailure

/**
 * IsThereAnyDeal implementation of the source-neutral [DealsSource] port (epic #205) — the **live**
 * deal source since Phase 2b.
 *
 * ITAD is game-centric (UUID game ids) and lacks several CheapShark fields (per-deal id, Steam rating,
 * Metacritic, deal rating, release date); those [Deal]/[DealDetails] fields are nullable and left null.
 * The DealsSource methods bridge the ITAD-shaped façade ([fetchDeals], [fetchGamePrices], [searchGames],
 * [lookupBySteamAppId], [fetchGameInfo], …) into the (UUID-migrated) domain via the ITAD→domain mappers.
 * Deal ids are synthesized as `"<gameUUID>:<shopId>"` since ITAD has no per-deal id. The `country=`
 * parameter is read from [RegionRepository] per call (regional pricing — Phase 3b, #212); ITAD has no
 * releases endpoint (releases moved to IGDB in 2c).
 */
internal class ItadSourceImpl(
    private val logger: Logger,
    private val shopsApi: ItadShopsApi,
    private val dealsApi: ItadDealsApi,
    private val gamesApi: ItadGamesApi,
    private val bundlesApi: ItadBundlesApi,
    private val remoteExceptionTransformer: RemoteExceptionTransformer,
    private val regionRepository: RegionRepository,
) : DealsSource {

    // --- DealsSource (implemented) ---

    override suspend fun fetchStores(): List<Store> =
        shopsApi.getShops()
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
            .map { it.toStore() }

    // --- DealsSource (ITAD live since #205 Phase 2b) ---

    override suspend fun fetchDealsForStore(query: SearchParameters?): List<Deal> {
        val storeId = query?.storeID
        val title = query?.title
        val limit = query?.pageSize
        val country = regionRepository.getSelectedCountryCode()
        return when {
            // Store deals: `/deals/v2?shops=<id>` returns each game's best deal at that shop.
            storeId != null -> fetchItadDeals(country = country, limit = limit, shops = storeId.toString()).map { it.toDeal() }
            // Title search (release lookup + Search screen): ITAD has no title filter on /deals/v2, so
            // resolve via game search → current prices → cheapest deal per game. Price/Steam-rating/
            // Metacritic filters in [query] aren't supported by ITAD and are ignored (epic #205 ADR trade-off).
            !title.isNullOrBlank() -> dealsByTitle(title, limit, country)
            else -> fetchItadDeals(country = country, limit = limit).map { it.toDeal() }
        }
    }

    override suspend fun fetchDeals(query: DealsQuery): List<Deal> =
        fetchItadDeals(
            country = regionRepository.getSelectedCountryCode(),
            offset = query.offset,
            limit = query.limit,
            sort = query.sort.apiValue,
            shops = query.shopIds.takeIf { it.isNotEmpty() }?.joinToString(separator = ","),
        ).map { it.toDeal() }

    override suspend fun fetchDealDetails(id: String): DealDetails {
        val gameId = gameIdFromDealId(id)
        val focusShopId = id.substringAfterLast(':').toIntOrNull() ?: 0
        val info = fetchGameInfo(gameId)
        val prices = fetchGamePrices(listOf(gameId), country = regionRepository.getSelectedCountryCode()).firstOrNull()
            ?: ItadGamePrices(gameId = gameId, historyLowAll = null, deals = persistentListOf())
        return prices.toDealDetails(title = info.title, boxart = info.boxart, focusShopId = focusShopId)
    }

    override suspend fun fetchGames(title: String, steamAppID: Int?, limit: Int?, pageNumber: Int?): List<Game> =
        when {
            steamAppID != null -> listOfNotNull(lookupBySteamAppId(steamAppID)?.toGame())
            title.isBlank() -> emptyList()
            else -> searchGames(title = title, results = limit).map { it.toGame() }
        }

    override suspend fun fetchGameDetails(id: String): GameDetails {
        val info = fetchGameInfo(id)
        val prices = fetchGamePrices(listOf(id), country = regionRepository.getSelectedCountryCode()).firstOrNull()
            ?: ItadGamePrices(gameId = id, historyLowAll = null, deals = persistentListOf())
        return prices.toGameDetails(title = info.title, boxart = info.boxart)
    }

    override suspend fun fetchGame(gameId: String): Game = fetchGameInfo(gameId).toGame()

    override suspend fun fetchPriceHistory(gameId: String, since: Long?): PriceHistory =
        fetchItadPriceHistory(
            gameId = gameId,
            country = regionRepository.getSelectedCountryCode(),
            // ITAD's `since` is an ISO-8601 instant; the domain bound is epoch-ms (source-neutral).
            since = since?.let { Instant.fromEpochMilliseconds(it).toString() },
        ).toPriceHistory(gameId)

    override suspend fun fetchBundles(): List<Bundle> =
        bundlesApi.getBundles(country = regionRepository.getSelectedCountryCode())
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
            .map { it.toBundle() }

    override suspend fun fetchGameMeta(gameId: String): GameMeta =
        gamesApi.getInfo(gameId)
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
            .toGameMeta()

    override suspend fun fetchBundlesForGame(gameId: String): List<Bundle> =
        gamesApi.getBundlesForGame(gameId)
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
            .map { it.toBundle() }

    override suspend fun fetchRegionalPrices(gameId: String, countries: List<Country>): List<RegionalPrice> =
        countries.mapNotNull { country ->
            val cheapest = fetchGamePrices(listOf(gameId), country = country.code)
                .firstOrNull()
                ?.deals
                ?.minByOrNull { it.price.amount }
                ?: return@mapNotNull null
            RegionalPrice(
                country = country,
                priceValue = cheapest.price.amount,
                priceDenominated = cheapest.price.denominated(),
                url = cheapest.url,
            )
        }

    /** Cheapest current deal per game for a title search; carries the game's title/boxart onto the deal. */
    private suspend fun dealsByTitle(title: String, limit: Int?, country: String): List<Deal> {
        val games = searchGames(title = title, results = limit)
        if (games.isEmpty()) return emptyList()
        val pricesByGameId = fetchGamePrices(games.map { it.id }, country = country).associateBy { it.gameId }
        return games.mapNotNull { game ->
            val cheapest = pricesByGameId[game.id]?.deals?.minByOrNull { it.price.amount } ?: return@mapNotNull null
            cheapest.copy(gameTitle = game.title, boxart = game.boxart).toDeal()
        }
    }

    // --- ITAD-shaped façade (fetch + map; consumed by the DealsSource methods above) ---

    suspend fun fetchItadDeals(
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
            .list
            .map { it.toItadDeal() }

    suspend fun fetchGamePrices(gameIds: List<String>, country: String? = null): List<ItadGamePrices> =
        gamesApi.getPrices(gameIds = gameIds, country = country)
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
            .map { it.toItadGamePrices() }

    suspend fun fetchItadPriceHistory(gameId: String, country: String? = null, since: String? = null): List<ItadPriceHistoryEntry> =
        gamesApi.getHistory(gameId = gameId, country = country, since = since)
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

    /** Basic game info (title + boxart) by UUID — drives the game-/deal-detail headers. */
    suspend fun fetchGameInfo(id: String): ItadGameSearchResult =
        gamesApi.getInfo(id)
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
            .toItadGameSearchResult()

    private companion object {
        private val TAG: String = ItadSourceImpl::class.simpleName.orEmpty()
    }
}
