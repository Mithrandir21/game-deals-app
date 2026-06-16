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
import pm.bam.gamedeals.domain.models.TagDiscoveryResult
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import pm.bam.gamedeals.domain.repositories.igdb.IgdbRepository
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.testing.TestingLoggingListener
import kotlin.test.Test
import kotlin.test.assertEquals
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
    fun discover_empty_filter_returns_empty_and_never_queries_igdb() = runTest {
        val result = impl.discover(IgdbTagFilter(), offset = 0)

        assertTrue(result.isEmpty())
        verifySuspend(exactly(0)) { igdbRepository.fetchGamesByTags(IgdbTagFilter(), 30, 0) }
    }

    @Test
    fun discover_resolves_only_page_games_batches_one_price_call_and_classifies_each_row() = runTest {
        // A: on ITAD + has a current deal → Priced(price). B: on ITAD, no current deal → Priced(null).
        // C: has Steam id but not on ITAD → SteamLinkOut. D: no Steam id at all → Unpriced.
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

        val result = impl.discover(filter, offset = 0)

        assertEquals(4, result.size)
        assertEquals(TagDiscoveryResult.Pricing.Priced("itad-A", priceA), result[0].pricing)
        assertEquals("https://images.igdb.com/igdb/image/upload/t_cover_big/ac.jpg", result[0].coverImageUrl)
        assertEquals(TagDiscoveryResult.Pricing.Priced("itad-B", null), result[1].pricing)
        assertEquals(TagDiscoveryResult.Pricing.SteamLinkOut("https://store.steampowered.com/app/30"), result[2].pricing)
        assertEquals(TagDiscoveryResult.Pricing.Unpriced, result[3].pricing)
        // Delta has no Steam id, so it must never trigger a lookup; pricing is one batched call.
        verifySuspend(exactly(0)) { gamesRepository.findGameIdBySteamAppId(any(), "Delta") }
        verifySuspend(exactly(1)) { gamesRepository.getGamePrices(listOf("itad-A", "itad-B")) }
    }

    @Test
    fun discover_passes_offset_and_page_size_through_to_igdb() = runTest {
        everySuspend { igdbRepository.fetchGamesByTags(filter, 15, 60) } returns emptyList()

        impl.discover(filter, offset = 60, pageSize = 15)

        verifySuspend(exactly(1)) { igdbRepository.fetchGamesByTags(filter, 15, 60) }
    }

    @Test
    fun discover_skips_pricing_entirely_when_no_page_game_has_a_steam_id() = runTest {
        everySuspend { igdbRepository.fetchGamesByTags(filter, 30, 0) } returns
            listOf(igdbGame(4L, "Delta", steamAppId = null))

        val result = impl.discover(filter, offset = 0)

        assertEquals(TagDiscoveryResult.Pricing.Unpriced, result.single().pricing)
        verifySuspend(exactly(0)) { gamesRepository.getGamePrices(any()) }
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
