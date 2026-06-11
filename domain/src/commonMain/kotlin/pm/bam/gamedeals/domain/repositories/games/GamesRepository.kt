package pm.bam.gamedeals.domain.repositories.games

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.db.cache.GameDetailsCacheEntry
import pm.bam.gamedeals.domain.db.dao.GameDetailsCacheDao
import pm.bam.gamedeals.domain.db.dao.GamesDao
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

    override suspend fun getPriceHistory(gameId: String): PriceHistory =
        dealsSource.fetchPriceHistory(gameId)

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
