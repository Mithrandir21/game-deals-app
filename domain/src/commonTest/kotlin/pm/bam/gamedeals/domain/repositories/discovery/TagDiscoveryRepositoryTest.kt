package pm.bam.gamedeals.domain.repositories.discovery

import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import dev.mokkery.verify.VerifyMode.Companion.exactly
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.db.cache.IgdbTagEntry
import pm.bam.gamedeals.domain.db.dao.IgdbTagDao
import pm.bam.gamedeals.domain.models.BundleGamePrice
import pm.bam.gamedeals.domain.models.IgdbGame
import pm.bam.gamedeals.domain.models.IgdbTag
import pm.bam.gamedeals.domain.models.IgdbTagDimension
import pm.bam.gamedeals.domain.models.IgdbTagFilter
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.igdb.IgdbRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.testing.TestingLoggingListener
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TagDiscoveryRepositoryTest {

    private val logger: Logger = TestingLoggingListener()
    private val igdbRepository: IgdbRepository = mock(MockMode.autoUnit)
    private val gamesRepository: GamesRepository = mock(MockMode.autoUnit)
    private val igdbTagDao: IgdbTagDao = mock(MockMode.autoUnit)

    private val now = 1_000_000L
    private val clock = Clock { now }

    private val impl = TagDiscoveryRepositoryImpl(logger, igdbRepository, gamesRepository, igdbTagDao, clock)

    private val filter = IgdbTagFilter(genreIds = persistentListOf(12L))

    @Test
    fun discover_empty_filter_returns_empty_page_without_querying_igdb() = runTest {
        val page = impl.discover(IgdbTagFilter(), offset = 0)

        assertTrue(page.results.isEmpty())
        assertTrue(page.endReached)
        verifySuspend(exactly(0)) { igdbRepository.fetchGamesByTags(IgdbTagFilter(), 30, 0) }
    }

    @Test
    fun discover_drops_untracked_and_steam_only_keeping_only_tracked_games() = runTest {
        // Alpha: tracked + current deal. Beta: tracked, no current deal. Gamma: Steam id but not on
        // ITAD (Steam-only → dropped). Delta: no Steam id (untracked → dropped, never looked up).
        val games = listOf(
            igdbGame(1L, "Alpha", steamAppId = 10, coverImageId = "ac"),
            igdbGame(2L, "Beta", steamAppId = 20),
            igdbGame(3L, "Gamma", steamAppId = 30),
            igdbGame(4L, "Delta", steamAppId = null),
        )
        everySuspend { igdbRepository.fetchGamesByTags(filter, 30, 0) } returns games
        everySuspend { gamesRepository.findGameIdBySteamAppId(10, "Alpha") } returns "itad-A"
        everySuspend { gamesRepository.findGameIdBySteamAppId(20, "Beta") } returns "itad-B"
        everySuspend { gamesRepository.findGameIdBySteamAppId(30, "Gamma") } returns null
        everySuspend { gamesRepository.getGamePrices(listOf("itad-A", "itad-B")) } returns listOf(priceA)

        val page = impl.discover(filter, offset = 0)

        // Only the two tracked games survive; the page reports the IGDB cursor + endReached (4 < 30).
        assertEquals(2, page.results.size)
        assertEquals("itad-A", page.results[0].gameId)
        assertEquals(priceA, page.results[0].price)
        assertEquals("https://images.igdb.com/igdb/image/upload/t_cover_big/ac.jpg", page.results[0].coverImageUrl)
        assertEquals("itad-B", page.results[1].gameId)
        assertEquals(null, page.results[1].price, "Tracked but no current deal → null price")
        assertEquals(4, page.nextOffset)
        assertTrue(page.endReached)
        // Untracked Delta must never trigger a lookup; only the tracked ids reach the single price call.
        verifySuspend(exactly(0)) { gamesRepository.findGameIdBySteamAppId(any(), "Delta") }
        verifySuspend(exactly(1)) { gamesRepository.getGamePrices(listOf("itad-A", "itad-B")) }
    }

    @Test
    fun discover_refills_from_the_next_igdb_page_when_filtering_thins_a_page() = runTest {
        // pageSize 2. Page 1 yields 1 tracked (the other is untracked); since the IGDB page was full,
        // discover pulls page 2 (1 more tracked) to fill, and resumes the cursor at IGDB offset 4.
        everySuspend { igdbRepository.fetchGamesByTags(filter, 2, 0) } returns
            listOf(igdbGame(1L, "Alpha", steamAppId = 10), igdbGame(2L, "Delta", steamAppId = null))
        everySuspend { igdbRepository.fetchGamesByTags(filter, 2, 2) } returns
            listOf(igdbGame(3L, "Gamma", steamAppId = 30), igdbGame(4L, "Echo", steamAppId = 40))
        everySuspend { gamesRepository.findGameIdBySteamAppId(10, "Alpha") } returns "itad-A"
        everySuspend { gamesRepository.findGameIdBySteamAppId(30, "Gamma") } returns "itad-C"
        everySuspend { gamesRepository.findGameIdBySteamAppId(40, "Echo") } returns null
        everySuspend { gamesRepository.getGamePrices(any()) } returns emptyList()

        val page = impl.discover(filter, offset = 0, pageSize = 2)

        assertEquals(listOf("itad-A", "itad-C"), page.results.map { it.gameId })
        assertEquals(4, page.nextOffset)
        assertFalse(page.endReached)
        verifySuspend(exactly(1)) { igdbRepository.fetchGamesByTags(filter, 2, 0) }
        verifySuspend(exactly(1)) { igdbRepository.fetchGamesByTags(filter, 2, 2) }
    }

    @Test
    fun discover_skips_pricing_when_no_page_game_resolves_to_itad() = runTest {
        // Single short page of untracked games → endReached, no lookups, no price call.
        everySuspend { igdbRepository.fetchGamesByTags(filter, 30, 0) } returns
            listOf(igdbGame(4L, "Delta", steamAppId = null))

        val page = impl.discover(filter, offset = 0)

        assertTrue(page.results.isEmpty())
        assertTrue(page.endReached)
        verifySuspend(exactly(0)) { gamesRepository.getGamePrices(any()) }
    }

    @Test
    fun discover_caps_the_number_of_igdb_pages_scanned_per_call() = runTest {
        // Every page is full but yields zero tracked games → discover must stop at the scan cap (5)
        // rather than looping forever, leaving endReached false so the user can keep paging.
        everySuspend { igdbRepository.fetchGamesByTags(any(), any(), any()) } returns
            listOf(igdbGame(1L, "U1", steamAppId = null), igdbGame(2L, "U2", steamAppId = null))

        val page = impl.discover(filter, offset = 0, pageSize = 2)

        assertTrue(page.results.isEmpty())
        assertFalse(page.endReached)
        assertEquals(10, page.nextOffset, "5 pages × 2 games")
        verifySuspend(exactly(5)) { igdbRepository.fetchGamesByTags(any(), any(), any()) }
    }

    @Test
    fun getTagVocabulary_cold_cache_fetches_concatenates_and_replaces_the_cache() = runTest {
        val vocab = listOf(IgdbTag(IgdbTagDimension.Genre, 12L, "Role-playing (RPG)", "role-playing-rpg"))
        val keywords = listOf(IgdbTag(IgdbTagDimension.Keyword, 270L, "roguelike", "roguelike"))
        everySuspend { igdbTagDao.getAll() } returns emptyList()
        everySuspend { igdbRepository.fetchTagVocabulary() } returns vocab
        everySuspend { igdbRepository.fetchCuratedKeywords(CURATED_KEYWORD_SLUGS) } returns keywords

        val result = impl.getTagVocabulary()

        assertEquals(vocab + keywords, result)
        verifySuspend(exactly(1)) { igdbTagDao.clear() }
        verifySuspend(exactly(1)) { igdbTagDao.upsertAll(any()) }
    }

    @Test
    fun getTagVocabulary_warm_cache_serves_from_room_without_querying_igdb() = runTest {
        everySuspend { igdbTagDao.getAll() } returns listOf(
            IgdbTagEntry("Genre", 12L, "Role-playing (RPG)", "role-playing-rpg", expires = now + 1),
        )

        val result = impl.getTagVocabulary()

        assertEquals(1, result.size)
        assertEquals(IgdbTagDimension.Genre, result.single().dimension)
        verifySuspend(exactly(0)) { igdbRepository.fetchTagVocabulary() }
    }

    @Test
    fun getTagVocabulary_serves_stale_cache_when_refetch_fails() = runTest {
        everySuspend { igdbTagDao.getAll() } returns listOf(
            IgdbTagEntry("Theme", 17L, "Fantasy", "fantasy", expires = now - 1), // expired → triggers refetch
        )
        everySuspend { igdbRepository.fetchTagVocabulary() } throws RuntimeException("IGDB down")

        val result = impl.getTagVocabulary()

        assertEquals(IgdbTagDimension.Theme, result.single().dimension)
    }

    private fun igdbGame(id: Long, name: String, steamAppId: Int?, coverImageId: String? = null): IgdbGame =
        IgdbGame(id = id, name = name, summary = null, coverImageId = coverImageId, steamAppId = steamAppId)

    private companion object {
        val priceA = BundleGamePrice(
            gameId = "itad-A",
            bestShopName = "Steam",
            bestPriceValue = 9.99,
            bestPriceDenominated = "$9.99",
            bestCutPercent = 50,
            historicalLowValue = 4.99,
            historicalLowDenominated = "$4.99",
        )
    }
}
