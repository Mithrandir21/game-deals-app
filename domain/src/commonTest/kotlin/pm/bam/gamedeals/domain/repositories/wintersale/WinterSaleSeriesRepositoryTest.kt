package pm.bam.gamedeals.domain.repositories.wintersale

import pm.bam.gamedeals.testing.TestingLoggingListener
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.db.dao.GameIdMappingDao
import pm.bam.gamedeals.domain.models.FollowedFranchise
import pm.bam.gamedeals.domain.repositories.franchise.FollowedFranchiseRepository
import pm.bam.gamedeals.domain.repositories.games.GamesRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WinterSaleSeriesRepositoryTest {
    @Test
    fun cold_open_respects_three_franchise_and_sixty_uncached_lookup_limit() = runTest {
        val fixture = fixture((1L..10L).map { followed(it) })
        fixture.source.games = (1L..10L).associateWith { id -> (1..30).map { WinterSaleSeriesGame(id * 100 + it, "Game $it", (id * 100 + it).toInt(), false) } }
        fixture.repo.load("GB", false)

        assertEquals(listOf(10L, 9L, 8L), fixture.source.calls)
        verifySuspend(exactly(60)) { fixture.games.findGameIdBySteamAppId(any(), any()) }
        assertTrue(fixture.store.entries.first { it.franchiseId == 8L }.complete.not())
    }

    @Test
    fun warm_map_prices_in_selected_region_and_hides_erotic_and_unknown_when_off() = runTest {
        val fixture = fixture(listOf(followed(1)))
        fixture.store.entries = listOf(WinterSaleSeriesMapEntry(1, 1L, 9_000L, true,
            listOf(WinterSaleSeriesMappedGame("safe", "Safe", false), WinterSaleSeriesMappedGame("adult", "Adult", true),
                WinterSaleSeriesMappedGame("unknown", "Unknown", null), WinterSaleSeriesMappedGame("zero", "Zero", false),
                WinterSaleSeriesMappedGame("missing", "Missing", false))))
        fixture.offers.values = mapOf("safe" to offer("safe"), "adult" to offer("adult"), "unknown" to offer("unknown"), "zero" to offer("zero", cut = 0))
        val off = fixture.repo.load("GB", false)
        val on = fixture.repo.load("US", true)

        assertEquals(listOf("safe"), off.games.map { it.game.gameId })
        assertEquals(3, on.games.size)
        assertEquals(listOf("GB", "US"), fixture.offers.countries)
        assertTrue(fixture.source.calls.isEmpty())
        assertFalse(off.checking)
    }

    @Test
    fun warm_ten_series_need_no_igdb_or_id_lookups_and_unfollowed_map_is_pruned() = runTest {
        val fixture = fixture((1L..10L).map { followed(it) })
        fixture.store.entries = (1L..10L).map { id -> WinterSaleSeriesMapEntry(id, id, 9_000L, true, emptyList()) } +
            WinterSaleSeriesMapEntry(99L, 99L, 9_000L, true, emptyList())

        val result = fixture.repo.load("GB", false)

        assertTrue(fixture.source.calls.isEmpty())
        verifySuspend(exactly(0)) { fixture.games.findGameIdBySteamAppId(any(), any()) }
        assertFalse(result.checking)
        assertEquals((1L..10L).toSet(), fixture.store.entries.map { it.franchiseId }.toSet())
    }

    @Test
    fun refollowed_series_rebuilds_instead_of_reusing_the_previous_follow_map() = runTest {
        val fixture = fixture(listOf(followed(2)))
        fixture.store.entries = listOf(WinterSaleSeriesMapEntry(2L, 1L, 9_000L, true, emptyList()))

        fixture.repo.load("GB", false)

        assertEquals(listOf(2L), fixture.source.calls)
        assertEquals(2L, fixture.store.entries.single().followedAtEpochMs)
    }

    @Test
    fun one_unknown_classification_in_overlapping_series_hides_the_game_when_adult_is_off() = runTest {
        val fixture = fixture(listOf(followed(1), followed(2)))
        fixture.store.entries = listOf(
            WinterSaleSeriesMapEntry(1, 1, 9_000L, true, listOf(WinterSaleSeriesMappedGame("shared", "Shared", false))),
            WinterSaleSeriesMapEntry(2, 2, 9_000L, true, listOf(WinterSaleSeriesMappedGame("shared", "Shared", null))),
        )

        assertTrue(fixture.repo.candidates(false).games.isEmpty())
        val visible = fixture.repo.candidates(true).games.single()
        assertEquals(null, visible.game.adult)
        assertEquals(listOf("Series 2", "Series 1"), visible.seriesNames)
    }

    @Test
    fun failed_franchise_keeps_its_previous_map_and_other_series_continue() = runTest {
        val fixture = fixture(listOf(followed(3), followed(2), followed(1)))
        fixture.store.entries = listOf(WinterSaleSeriesMapEntry(2, 2L, -700_000_000L, true,
            listOf(WinterSaleSeriesMappedGame("old", "Old", false))))
        fixture.source.failId = 2L
        fixture.offers.values = mapOf("old" to offer("old"))
        val section = fixture.repo.load("GB", false)

        assertEquals(listOf("old"), section.games.map { it.game.gameId })
        assertTrue(section.unavailable)
        assertTrue(section.checking)
        assertEquals(listOf(3L, 2L, 1L), fixture.source.calls)
        assertTrue(fixture.store.entries.any { it.franchiseId == 2L && it.games.single().itadId == "old" })
    }

    private fun fixture(followed: List<FollowedFranchise>): Fixture {
        val followedRepo = mock<FollowedFranchiseRepository>(MockMode.autofill)
        val games = mock<GamesRepository>(MockMode.autofill)
        val dao = mock<GameIdMappingDao>(MockMode.autofill)
        val source = FakeSource()
        val store = FakeStore()
        val offers = FakeOffers()
        everySuspend { followedRepo.getFollowed() } returns followed
        everySuspend { dao.get(any()) } returns null
        everySuspend { games.findGameIdBySteamAppId(any(), any()) } returns null
        val repo = WinterSaleSeriesRepositoryImpl(followedRepo, source, games, dao, store, offers, Clock { 10_000L }, TestingLoggingListener())
        return Fixture(repo, source, store, offers, games)
    }

    private data class Fixture(val repo: WinterSaleSeriesRepository, val source: FakeSource, val store: FakeStore,
        val offers: FakeOffers, val games: GamesRepository)

    private class FakeSource : WinterSaleSeriesSource {
        var games = emptyMap<Long, List<WinterSaleSeriesGame>>()
        var failId: Long? = null
        val calls = mutableListOf<Long>()
        override suspend fun fetch(franchiseId: Long, limit: Int): List<WinterSaleSeriesGame> {
            calls += franchiseId
            if (franchiseId == failId) error("IGDB unavailable")
            return games[franchiseId].orEmpty()
        }
    }

    private class FakeStore : WinterSaleSeriesMapStore {
        var entries = emptyList<WinterSaleSeriesMapEntry>()
        override suspend fun get() = entries
        override suspend fun replace(entries: List<WinterSaleSeriesMapEntry>) { this.entries = entries }
    }

    private class FakeOffers : WinterSaleOffersRepository {
        var values = emptyMap<String, WinterSaleOffer>()
        val countries = mutableListOf<String>()
        override suspend fun load(gameIds: List<String>, country: String): WinterSaleOfferLoad {
            countries += country
            return WinterSaleOfferLoad(country, 10_000L, gameIds.associateWith { id ->
                values[id]?.copy(country = country)?.let { WinterSalePriceStatus.Priced(it) } ?: WinterSalePriceStatus.NoCurrentOffer
            })
        }
    }

    private fun followed(id: Long) = FollowedFranchise(id, "Series $id", id)
    private fun offer(id: String, cut: Int = 20) = WinterSaleOffer(id, "GB", 1, "Shop", WinterSaleMoney(10.0, "GBP"), null, cut,
        "https://example.com/$id", null, emptyList(), null, null, null)
}
