package pm.bam.gamedeals.domain.repositories.games

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.db.cache.GameDetailsCacheEntry
import pm.bam.gamedeals.domain.db.cache.GameIdMappingEntry
import pm.bam.gamedeals.domain.db.cache.PriceHistoryCacheEntry
import pm.bam.gamedeals.domain.db.dao.GameDetailsCacheDao
import pm.bam.gamedeals.domain.db.dao.GameIdMappingDao
import pm.bam.gamedeals.domain.db.dao.GamesDao
import pm.bam.gamedeals.domain.db.dao.PriceHistoryCacheDao
import pm.bam.gamedeals.domain.models.Bundle
import pm.bam.gamedeals.domain.models.GameDetails
import pm.bam.gamedeals.domain.models.GameMeta
import pm.bam.gamedeals.domain.models.PriceHistory
import pm.bam.gamedeals.domain.repositories.region.RegionRepository
import pm.bam.gamedeals.domain.source.DealsSource
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.fixtures.game
import pm.bam.gamedeals.testing.fixtures.gameDetails
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class GamesRepositoryTest {

    private val logger: Logger = TestingLoggingListener()
    private val gamesDao: GamesDao = mock(MockMode.autoUnit)
    private val dealsSource: DealsSource = mock(MockMode.autoUnit)
    private val gameDetailsCacheDao: GameDetailsCacheDao = mock(MockMode.autoUnit)
    private val priceHistoryCacheDao: PriceHistoryCacheDao = mock(MockMode.autoUnit)
    private val gameIdMappingDao: GameIdMappingDao = mock(MockMode.autoUnit)

    private val now = 1_000_000L
    private val clock = Clock { now }
    private val json = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    private val country = "US"
    private val regionRepository: RegionRepository = mock(MockMode.autoUnit) {
        everySuspend { getSelectedCountryCode() } returns country
    }

    private val impl = GamesRepositoryImpl(logger, gamesDao, dealsSource, clock, regionRepository, gameDetailsCacheDao, priceHistoryCacheDao, gameIdMappingDao, json)

    @Test
    fun observe_games_empty_cache_triggers_refresh_and_stamps_expires() = runTest {
        val game = game()

        every { gamesDao.observeAllGames() } returns flowOf(emptyList())
        everySuspend { gamesDao.getAllGames() } returns emptyList()
        everySuspend { dealsSource.fetchGames("") } returns listOf(game)

        val result = impl.observeGames().first()
        assertTrue(result.isEmpty())

        verify(exactly(1)) { gamesDao.observeAllGames() }
        verifySuspend(exactly(1)) { dealsSource.fetchGames("") }
        verifySuspend(exactly(1)) { gamesDao.addGames(game.copy(expires = now + GAMES_TTL_MILLIS)) }
    }

    @Test
    fun observe_games_fresh_cache_does_not_refresh() = runTest {
        val fresh = game(expires = now + 10_000)

        every { gamesDao.observeAllGames() } returns flowOf(listOf(fresh))
        everySuspend { gamesDao.getAllGames() } returns listOf(fresh)

        val result = impl.observeGames().first()
        assertTrue(result.isNotEmpty())

        verifySuspend(exactly(0)) { dealsSource.fetchGames("") }
        verifySuspend(exactly(1)) { gamesDao.getAllGames() }
    }

    @Test
    fun get_game_details_cold_cache_fetches_caches_and_returns() = runTest {
        val gameId = "1"
        val details = gameDetails()

        everySuspend { gameDetailsCacheDao.get(gameId, country) } returns null
        everySuspend { dealsSource.fetchGameDetails(gameId) } returns details

        val result = impl.getGameDetails(gameId)
        assertEquals(details, result)

        // Cold cache: fetch, persist the region-keyed blob, return the fresh value.
        verifySuspend(exactly(1)) { dealsSource.fetchGameDetails(gameId) }
        verifySuspend(exactly(1)) { gameDetailsCacheDao.upsert(any()) }
    }

    @Test
    fun get_game_details_fresh_cache_returns_decoded_without_fetch() = runTest {
        val gameId = "1"
        val details = gameDetails()
        val entry = GameDetailsCacheEntry(
            gameId = gameId,
            country = country,
            json = json.encodeToString(GameDetails.serializer(), details),
            expires = now + 10_000,
        )
        everySuspend { gameDetailsCacheDao.get(gameId, country) } returns entry

        val result = impl.getGameDetails(gameId)
        assertEquals(details, result)

        // Fresh cache: decode the blob, no network fetch.
        verifySuspend(exactly(0)) { dealsSource.fetchGameDetails(any()) }
    }

    @Test
    fun get_game_details_cold_cache_refresh_failure_surfaces() = runTest {
        val gameId = "1"

        everySuspend { gameDetailsCacheDao.get(gameId, country) } returns null
        everySuspend { dealsSource.fetchGameDetails(gameId) } throws Exception("network down")

        // Cold cache with no stale fallback: the failure surfaces (retryable).
        assertFailsWith<Exception> { impl.getGameDetails(gameId) }
    }

    @Test
    fun refresh_games_unforced_expired_entry_fetches_and_stamps_expires() = runTest {
        val expired = game(gameID = "old", expires = now - 1)
        val fetched = game(gameID = "new")

        everySuspend { gamesDao.getAllGames() } returns listOf(expired)
        everySuspend { dealsSource.fetchGames("") } returns listOf(fetched)

        impl.refreshGames()

        verifySuspend(exactly(1)) { dealsSource.fetchGames("") }
        verifySuspend(exactly(1)) { gamesDao.addGames(fetched.copy(expires = now + GAMES_TTL_MILLIS)) }
    }

    @Test
    fun refresh_games_unforced_all_fresh_skips_fetch() = runTest {
        val fresh = game(expires = now + 10_000)

        everySuspend { gamesDao.getAllGames() } returns listOf(fresh)

        impl.refreshGames()

        verifySuspend(exactly(0)) { dealsSource.fetchGames("") }
        verifySuspend(exactly(1)) { gamesDao.getAllGames() }
    }

    @Test
    fun get_price_history_cold_cache_fetches_full_series_caches_and_returns() = runTest {
        val gameId = "uuid-1"
        val series = priceHistory(gameId, point(1_000L, 9.99, "$9.99"))

        everySuspend { priceHistoryCacheDao.get(gameId, country) } returns null
        everySuspend { dealsSource.fetchPriceHistory(gameId, null) } returns series

        val result = impl.getPriceHistory(gameId)
        assertEquals(series, result)

        // Cold cache: full fetch (since = null), persist the region-keyed blob.
        verifySuspend(exactly(1)) { dealsSource.fetchPriceHistory(gameId, null) }
        verifySuspend(exactly(1)) { priceHistoryCacheDao.upsert(any()) }
    }

    @Test
    fun get_price_history_fresh_cache_returns_decoded_without_fetch() = runTest {
        val gameId = "uuid-1"
        val series = priceHistory(gameId, point(1_000L, 9.99, "$9.99"))
        everySuspend { priceHistoryCacheDao.get(gameId, country) } returns entryFor(gameId, series, expires = now + 10_000)

        val result = impl.getPriceHistory(gameId)
        assertEquals(series, result)

        // Fresh cache: decode the blob, no network fetch.
        verifySuspend(exactly(0)) { dealsSource.fetchPriceHistory(any(), any()) }
    }

    @Test
    fun get_price_history_stale_cache_tops_up_incrementally_and_merges() = runTest {
        val gameId = "uuid-1"
        val cached = priceHistory(gameId, point(1_000L, 5.0, "$5"), point(2_000L, 4.0, "$4"))
        // Stale entry (expired): the latest cached point (2_000) is the `since` lower bound.
        everySuspend { priceHistoryCacheDao.get(gameId, country) } returns entryFor(gameId, cached, expires = now - 1)
        // Delta re-returns the boundary point (2_000) plus a new one (3_000).
        val delta = priceHistory(gameId, point(2_000L, 4.0, "$4"), point(3_000L, 3.0, "$3"))
        everySuspend { dealsSource.fetchPriceHistory(gameId, 2_000L) } returns delta

        val result = impl.getPriceHistory(gameId)

        // Merged, de-duplicated by timestamp, sorted oldest → newest.
        val expected = priceHistory(gameId, point(1_000L, 5.0, "$5"), point(2_000L, 4.0, "$4"), point(3_000L, 3.0, "$3"))
        assertEquals(expected, result)
        verifySuspend(exactly(1)) { dealsSource.fetchPriceHistory(gameId, 2_000L) }
        verifySuspend(exactly(1)) { priceHistoryCacheDao.upsert(any()) }
    }

    @Test
    fun get_price_history_stale_cache_refresh_failure_serves_stale() = runTest {
        val gameId = "uuid-1"
        val cached = priceHistory(gameId, point(1_000L, 5.0, "$5"))
        everySuspend { priceHistoryCacheDao.get(gameId, country) } returns entryFor(gameId, cached, expires = now - 1)
        everySuspend { dealsSource.fetchPriceHistory(gameId, 1_000L) } throws Exception("network down")

        // Warm cache + failed refresh: fall back to the stale series (D7), don't throw, don't upsert.
        val result = impl.getPriceHistory(gameId)
        assertEquals(cached, result)
        verifySuspend(exactly(0)) { priceHistoryCacheDao.upsert(any()) }
    }

    private fun point(timestampEpochMs: Long, value: Double, denominated: String) =
        PriceHistory.PricePoint(timestampEpochMs, value, denominated)

    private fun priceHistory(gameId: String, vararg points: PriceHistory.PricePoint) =
        PriceHistory(gameID = gameId, points = persistentListOf(*points))

    private fun entryFor(gameId: String, series: PriceHistory, expires: Long) =
        PriceHistoryCacheEntry(
            gameId = gameId,
            country = country,
            json = json.encodeToString(PriceHistory.serializer(), series),
            fetchedAt = expires - 1,
            expires = expires,
        )

    @Test
    fun find_game_id_by_steam_app_id_cold_cache_looks_up_caches_and_returns() = runTest {
        val match = game().copy(gameID = "12345")
        everySuspend { gameIdMappingDao.get(1240440) } returns null
        everySuspend { dealsSource.fetchGames(title = "Halo Infinite", steamAppID = 1240440, limit = 1) } returns listOf(match)

        val result = impl.findGameIdBySteamAppId(steamAppId = 1240440, title = "Halo Infinite")

        assertEquals("12345", result)
        verifySuspend(exactly(1)) { dealsSource.fetchGames(title = "Halo Infinite", steamAppID = 1240440, limit = 1) }
        // The resolved mapping is cached.
        verifySuspend(exactly(1)) { gameIdMappingDao.upsert(GameIdMappingEntry(1240440, "12345", now + GAME_ID_MAPPING_TTL_MILLIS)) }
    }

    @Test
    fun find_game_id_by_steam_app_id_fresh_cache_returns_mapping_without_lookup() = runTest {
        everySuspend { gameIdMappingDao.get(1240440) } returns GameIdMappingEntry(1240440, "12345", expires = now + 10_000)

        val result = impl.findGameIdBySteamAppId(steamAppId = 1240440, title = "Halo Infinite")

        assertEquals("12345", result)
        verifySuspend(exactly(0)) { dealsSource.fetchGames(title = any(), steamAppID = any(), limit = any()) }
    }

    @Test
    fun find_game_id_by_steam_app_id_genuine_miss_is_not_cached() = runTest {
        everySuspend { gameIdMappingDao.get(999999) } returns null
        everySuspend { dealsSource.fetchGames(title = "Unknown Game", steamAppID = 999999, limit = 1) } returns emptyList()

        val result = impl.findGameIdBySteamAppId(steamAppId = 999999, title = "Unknown Game")

        assertEquals(null, result)
        // A genuine "no match" must not be cached, so a later retry can still resolve (D3).
        verifySuspend(exactly(0)) { gameIdMappingDao.upsert(any()) }
    }

    @Test
    fun find_game_id_by_steam_app_id_lookup_failure_serves_stale_mapping() = runTest {
        // Stale (expired) mapping present + the lookup throws → fall back to the stale UUID.
        everySuspend { gameIdMappingDao.get(1240440) } returns GameIdMappingEntry(1240440, "12345", expires = now - 1)
        everySuspend { dealsSource.fetchGames(title = "Halo Infinite", steamAppID = 1240440, limit = 1) } throws Exception("network down")

        val result = impl.findGameIdBySteamAppId(steamAppId = 1240440, title = "Halo Infinite")

        assertEquals("12345", result)
        verifySuspend(exactly(0)) { gameIdMappingDao.upsert(any()) }
    }

    @Test
    fun find_game_id_by_steam_app_id_cold_cache_lookup_failure_returns_null() = runTest {
        everySuspend { gameIdMappingDao.get(1240440) } returns null
        everySuspend { dealsSource.fetchGames(title = "Halo Infinite", steamAppID = 1240440, limit = 1) } throws Exception("network down")

        val result = impl.findGameIdBySteamAppId(steamAppId = 1240440, title = "Halo Infinite")

        assertEquals(null, result, "no stale mapping to fall back to → null")
    }

    @Test
    fun get_game_meta_fetches_live_from_deals_source() = runTest {
        val gameId = "uuid-1"
        val meta = GameMeta(
            gameId = gameId,
            developers = persistentListOf("343 Industries"),
            tags = persistentListOf("Shooter"),
            players = GameMeta.Players(recent = 16763, peak = 30000),
        )
        everySuspend { dealsSource.fetchGameMeta(gameId) } returns meta

        val result = impl.getGameMeta(gameId)

        // No persistent cache (volatile player counts): always a live fetch, value passes straight through.
        assertEquals(meta, result)
        verifySuspend(exactly(1)) { dealsSource.fetchGameMeta(gameId) }
    }

    @Test
    fun get_bundles_for_game_fetches_live_from_deals_source() = runTest {
        val gameId = "uuid-1"
        val bundles = listOf(
            Bundle(
                id = 16232,
                title = "Humble Choice",
                storeName = "Humble Bundle",
                url = "https://humble.example/c/123",
                expiryEpochMs = null,
                gameCount = 8,
                priceDenominated = "$14.99",
                games = persistentListOf(),
            )
        )
        everySuspend { dealsSource.fetchBundlesForGame(gameId) } returns bundles

        val result = impl.getBundlesForGame(gameId)

        assertEquals(bundles, result)
        verifySuspend(exactly(1)) { dealsSource.fetchBundlesForGame(gameId) }
    }
}
