package pm.bam.gamedeals.remote.itad

import com.skydoves.sandwich.getOrThrow
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import pm.bam.gamedeals.domain.models.Bundle
import pm.bam.gamedeals.domain.models.BundleGamePrice
import pm.bam.gamedeals.domain.models.Country
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.DealDetails
import pm.bam.gamedeals.domain.models.DealsFilter
import pm.bam.gamedeals.domain.models.DealsQuery
import pm.bam.gamedeals.domain.models.Game
import pm.bam.gamedeals.domain.models.GameDetails
import pm.bam.gamedeals.domain.models.GameMeta
import pm.bam.gamedeals.domain.models.PriceHistory
import pm.bam.gamedeals.domain.models.ProductType
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
import pm.bam.gamedeals.remote.itad.mappers.isGameLikeProductType
import pm.bam.gamedeals.remote.itad.mappers.toDeal
import pm.bam.gamedeals.remote.itad.mappers.toDealDetails
import pm.bam.gamedeals.remote.itad.mappers.toGame
import pm.bam.gamedeals.remote.itad.mappers.toGameDetails
import pm.bam.gamedeals.remote.itad.mappers.toGameMeta
import pm.bam.gamedeals.remote.itad.mappers.toItadDeal
import pm.bam.gamedeals.remote.itad.mappers.toItadGamePrices
import pm.bam.gamedeals.remote.itad.mappers.toItadGameSearchResult
import pm.bam.gamedeals.remote.itad.mappers.toBundle
import pm.bam.gamedeals.remote.itad.mappers.toBundleGamePrice
import pm.bam.gamedeals.remote.itad.mappers.toItadPriceHistoryEntry
import pm.bam.gamedeals.remote.itad.mappers.toPriceHistory
import pm.bam.gamedeals.remote.itad.mappers.toStore
import pm.bam.gamedeals.remote.itad.models.ItadDeal
import pm.bam.gamedeals.remote.itad.models.RemoteItadDealsFilter
import pm.bam.gamedeals.remote.itad.models.RemoteItadDealsRequest
import pm.bam.gamedeals.remote.itad.models.toRemoteFilter
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
@OptIn(ExperimentalSerializationApi::class) // RemoteItadDealsRequest's filter uses @EncodeDefault
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
            // Store deals: `/deals/v2` with shops=[id] returns each game's best deal at that shop.
            storeId != null -> fetchItadDeals(country = country, limit = limit, shops = listOf(storeId)).map { it.toDeal() }
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
            sort = query.sortApiValue,
            shops = query.shopIds,
            // Only send `mature=true` to opt in; the default (param omitted) excludes adult titles.
            mature = query.mature.takeIf { it },
            filter = query.filter,
        ).map { it.toDeal() }

    override suspend fun fetchDealDetails(id: String): DealDetails {
        val gameId = gameIdFromDealId(id)
        val focusShopId = id.substringAfterLast(':').toIntOrNull() ?: 0
        val info = fetchGameInfo(gameId)
        val prices = fetchGamePrices(listOf(gameId), country = regionRepository.getSelectedCountryCode()).firstOrNull()
            ?: ItadGamePrices(gameId = gameId, historyLowAll = null, deals = persistentListOf())
        return prices.toDealDetails(title = info.title, artwork = info.artwork, focusShopId = focusShopId)
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
        return prices.toGameDetails(title = info.title, artwork = info.artwork)
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
            // After stripping software/hardware tier games, a bundle left with no games is a non-game
            // bundle (e.g. a Fanatical software bundle) — drop it from the games-only Bundles surfaces.
            .filter { it.games.isNotEmpty() }

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

    override suspend fun fetchBundleGamePrices(gameIds: List<String>): List<BundleGamePrice> {
        if (gameIds.isEmpty()) return emptyList()
        return fetchGamePrices(gameIds, country = regionRepository.getSelectedCountryCode())
            .map { it.toBundleGamePrice() }
    }

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
            cheapest.copy(gameTitle = game.title, artwork = game.artwork).toDeal()
        }
    }

    // --- ITAD-shaped façade (fetch + map; consumed by the DealsSource methods above) ---

    suspend fun fetchItadDeals(
        country: String? = null,
        offset: Int? = null,
        limit: Int? = null,
        sort: String? = null,
        shops: List<Int>? = null,
        mature: Boolean? = null,
        filter: DealsFilter? = null,
    ): List<ItadDeal> =
        dealsApi.getDeals(
            RemoteItadDealsRequest(
                country = country,
                offset = offset,
                limit = limit,
                sort = sort,
                mature = mature,
                shops = shops?.takeIf { it.isNotEmpty() },
                filter = dealsFilterWithExclusion(filter),
            )
        )
            .log(logger, tag = TAG)
            .mapAnyFailure { remoteExceptionTransformer.transformApiException(this) }
            .getOrThrow()
            .list
            .map { it.toItadDeal() }

    /**
     * The wire `filter` for **every** `/deals/v2` request. ITAD's deals response carries no per-item type,
     * so software/hardware can only be excluded server-side: each request constrains `type` to the
     * game-like products ([ProductType] Game/DLC/Bundle = ids 1/2/3), keeping non-games out of Home, the
     * Store screen and Deals browse alike — silently, without a user-visible filter. The user's own type
     * narrowing (when set) already lives within that set, so it is honoured verbatim; otherwise the full
     * game-like set is the baseline.
     */
    private fun dealsFilterWithExclusion(filter: DealsFilter?): RemoteItadDealsFilter {
        val wire = filter?.takeIf { !it.isEmpty() }?.toRemoteFilter(currentYear()) ?: RemoteItadDealsFilter()
        return if (wire.type.isNullOrEmpty()) wire.copy(type = GAME_LIKE_TYPE_IDS) else wire
    }

    /** Current calendar year in the device timezone — resolves the Deals filter's release-recency windows. */
    @OptIn(ExperimentalTime::class)
    private fun currentYear(): Int =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).year

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
            // ITAD search mixes in software/hardware, which it leaves with a null `type` (only games/DLC/
            // packages are typed). Keep just the game-like types so the Deals search overlay and the
            // title/release lookups stay game-only. Covers `dealsByTitle`.
            .filter { it.type.isGameLikeProductType() }
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

        /** ITAD `type` ids the app surfaces — Game/DLC/Bundle ([ProductType]); excludes Software(7)/Hardware(9). */
        private val GAME_LIKE_TYPE_IDS: List<Int> = ProductType.entries.map { it.apiValue }.sorted()
    }
}
