package pm.bam.gamedeals.domain.repositories.games

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.db.cache.GameDetailsCacheEntry
import pm.bam.gamedeals.domain.db.cache.PriceHistoryCacheEntry
import pm.bam.gamedeals.domain.db.dao.GameDetailsCacheDao
import pm.bam.gamedeals.domain.db.dao.GamesDao
import pm.bam.gamedeals.domain.db.dao.PriceHistoryCacheDao
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.Game
import pm.bam.gamedeals.domain.models.GameDetails
import pm.bam.gamedeals.domain.models.PriceHistory
import pm.bam.gamedeals.domain.models.SearchParameters
import pm.bam.gamedeals.domain.repositories.cache.CachedResource
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
import pm.bam.gamedeals.domain.source.DealsSource
import pm.bam.gamedeals.domain.utils.millisInDay
import pm.bam.gamedeals.domain.utils.millisInHour
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.debug

/** Games are metadata (7-day tier — ITAD caching strategy §4 / Phase 1). */
internal val GAMES_TTL_MILLIS = millisInDay * 7

/** Game details are the transact tier — short TTL, fresh-blocking (ITAD caching strategy §4.2 / Phase 3). */
internal val GAME_DETAILS_TTL_MILLIS = millisInHour * 2

/**
 * Price history is append-only metadata — a long SWR TTL is enough since the refresh is incremental
 * (only points newer than the latest cached one are fetched). ITAD caching strategy §4 / Phase 4.
 */
internal val PRICE_HISTORY_TTL_MILLIS = millisInDay

interface GamesRepository {
    fun observeGames(): Flow<List<Game>>
    suspend fun searchGames(query: String): List<Game>

    @ExperimentalSerializationApi
    suspend fun searchGames(searchParameters: SearchParameters): List<Deal>

    @ExperimentalSerializationApi
    suspend fun getReleaseDeal(gameTitle: String): Deal?

    suspend fun getGameDetails(dealId: String): GameDetails

    /** The game's historical low-price time series for the price-history chart (#208). */
    suspend fun getPriceHistory(gameId: String): PriceHistory

    suspend fun refreshGames()

    suspend fun findGameIdBySteamAppId(steamAppId: Int, title: String): String?
}

internal class GamesRepositoryImpl(
    private val logger: Logger,
    private val gamesDao: GamesDao,
    private val dealsSource: DealsSource,
    private val clock: Clock,
    private val regionRepository: RegionRepository,
    private val gameDetailsCacheDao: GameDetailsCacheDao,
    private val priceHistoryCacheDao: PriceHistoryCacheDao,
    private val json: Json,
) : GamesRepository {

    private val cache = CachedResource(
        clock = clock,
        read = { gamesDao.getAllGames() },
        expiresAtMillis = { it.expires },
        refresh = {
            val expiresAt = clock.nowMillis() + GAMES_TTL_MILLIS
            dealsSource.fetchGames("")
                .map { it.copy(expires = expiresAt) }
                .let { gamesDao.addGames(*it.toTypedArray()) }
        }
    )

    override fun observeGames(): Flow<List<Game>> =
        gamesDao.observeAllGames()
            .onStart { refreshGames() }

    override suspend fun searchGames(query: String): List<Game> =
        dealsSource.fetchGames(query)

    @ExperimentalSerializationApi
    override suspend fun searchGames(searchParameters: SearchParameters): List<Deal> =
        dealsSource.fetchDealsForStore(searchParameters)

    @ExperimentalSerializationApi
    override suspend fun getReleaseDeal(gameTitle: String): Deal? =
        dealsSource.fetchDealsForStore(SearchParameters(title = gameTitle, exact = true))
            .firstOrNull()

    /**
     * Game details for the transact surface. Cached per `(gameId, country)` at a short 2h TTL and read
     * **fresh-blocking** (see [DealsRepository.getDeal][pm.bam.gamedeals.domain.repositories.deals.DealsRepository.getDeal]).
     * Bounded by serve-stale-on-error (D7): a warm cache falls back to stale on a failed refresh; a
     * cold cache surfaces the failure (retryable). The id is the game UUID.
     */
    override suspend fun getGameDetails(dealId: String): GameDetails {
        val gameId = dealId
        val country = regionRepository.getSelectedCountryCode()
        var fetched: GameDetails? = null
        val cache = CachedResource(
            clock = clock,
            read = { gameDetailsCacheDao.get(gameId, country)?.let(::listOf) ?: emptyList() },
            expiresAtMillis = { it.expires },
            refresh = {
                val details = dealsSource.fetchGameDetails(gameId)
                fetched = details
                gameDetailsCacheDao.upsert(
                    GameDetailsCacheEntry(
                        gameId = gameId,
                        country = country,
                        json = json.encodeToString(GameDetails.serializer(), details),
                        expires = clock.nowMillis() + GAME_DETAILS_TTL_MILLIS,
                    )
                )
            },
        )
        cache.refreshIfNeeded()
        fetched?.let { return it } // just refreshed — return the fresh value without a re-read/decode
        val cached = gameDetailsCacheDao.get(gameId, country)
            ?: error("Game details for $gameId ($country) missing after refresh")
        return json.decodeFromString(GameDetails.serializer(), cached.json)
    }

    /**
     * Price history for the chart, cached per `(gameId, country)` at a long SWR TTL (ITAD caching
     * strategy, Phase 4). The series is append-only, so a stale entry is **topped-up incrementally**:
     * only points newer than the latest cached one are fetched ([DealsSource.fetchPriceHistory]'s `since`)
     * and merged, rather than refetching the whole log. A cold cache fetches the full series.
     *
     * Bounded by serve-stale-on-error (D7): on a warm cache a failed refresh falls back to the cached
     * series; on a cold cache the failure surfaces (the caller — `GameViewModel` — already treats price
     * history as best-effort and hides the chart on failure).
     */
    override suspend fun getPriceHistory(gameId: String): PriceHistory {
        val country = regionRepository.getSelectedCountryCode()
        val cachedEntry = priceHistoryCacheDao.get(gameId, country)
        val cachedHistory = cachedEntry?.let { json.decodeFromString(PriceHistory.serializer(), it.json) }
        var refreshed: PriceHistory? = null
        val cache = CachedResource(
            clock = clock,
            read = { cachedEntry?.let(::listOf) ?: emptyList() },
            expiresAtMillis = { it.expires },
            refresh = {
                // Incremental: top-up from the latest cached point; full fetch when the cache is cold.
                val since = cachedHistory?.points?.maxOfOrNull { it.timestampEpochMs }
                val fetched = dealsSource.fetchPriceHistory(gameId, since)
                val merged = cachedHistory?.let { mergePriceHistory(it, fetched) } ?: fetched
                refreshed = merged
                val now = clock.nowMillis()
                priceHistoryCacheDao.upsert(
                    PriceHistoryCacheEntry(
                        gameId = gameId,
                        country = country,
                        json = json.encodeToString(PriceHistory.serializer(), merged),
                        fetchedAt = now,
                        expires = now + PRICE_HISTORY_TTL_MILLIS,
                    )
                )
            },
        )
        cache.refreshIfNeeded()
        // Fresh (no refresh) or serve-stale-on-error → the cached series; otherwise the just-merged one.
        return refreshed ?: cachedHistory ?: PriceHistory(gameID = gameId, points = persistentListOf())
    }

    /**
     * Merges an incremental [fetched] top-up into the [cached] series, de-duplicating by timestamp
     * (the boundary point may be re-returned) with the freshly-fetched point winning, sorted oldest →
     * newest. Both series are for the same `(gameId, country)`.
     */
    private fun mergePriceHistory(cached: PriceHistory, fetched: PriceHistory): PriceHistory {
        val byTimestamp = LinkedHashMap<Long, PriceHistory.PricePoint>()
        cached.points.forEach { byTimestamp[it.timestampEpochMs] = it }
        fetched.points.forEach { byTimestamp[it.timestampEpochMs] = it }
        val points = byTimestamp.values.sortedBy { it.timestampEpochMs }.toImmutableList()
        return PriceHistory(gameID = fetched.gameID, points = points)
    }

    override suspend fun refreshGames() {
        val refreshed = cache.refreshIfNeeded()
        debug(logger) { "Games refresh needed: $refreshed" }
    }

    override suspend fun findGameIdBySteamAppId(steamAppId: Int, title: String): String? =
        runCatching {
            dealsSource.fetchGames(title = title, steamAppID = steamAppId, limit = 1)
                .firstOrNull()
                ?.gameID
        }.getOrNull()
}
