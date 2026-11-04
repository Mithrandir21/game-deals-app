package pm.bam.gamedeals.domain.repositories.wintersale

import pm.bam.gamedeals.testing.TestingLoggingListener
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.domain.models.GameArtwork
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WinterSaleGuideRepositoryTest {
    @Test
    fun one_shared_price_pass_deduplicates_sections_and_preserves_all_reasons() = runTest {
        val candidates = FakeCandidates()
        candidates.wait = WinterSaleRawSection(listOf(raw("wait"), raw("shared")))
        candidates.popular = WinterSaleRawSection(listOf(raw("series"), raw("popular"), raw("ordinary")))
        val series = FakeSeries(WinterSaleSeriesRawSection(listOf(
            WinterSaleSeriesRawCandidate(raw("shared"), listOf("Series A")),
            WinterSaleSeriesRawCandidate(raw("series"), listOf("Series B")),
        ), checking = false, unavailable = false, moreSeriesFollowed = false))
        val offers = FakeOffers(mapOf(
            "wait" to offer("wait", 40), "shared" to offer("shared", 30), "series" to offer("series", 20),
            "popular" to offer("popular", 10), "ordinary" to offer("ordinary", 0),
        ))

        val result = WinterSaleGuideRepositoryImpl(candidates, series, offers, TestingLoggingListener()).load("GB", false, 1L, true)

        assertEquals(1, offers.calls.size)
        assertEquals(setOf("wait", "shared", "series", "popular", "ordinary"), offers.calls.single().toSet())
        assertEquals(listOf("wait", "shared"), result.waitlist.cards.map { it.candidate.gameId })
        assertEquals(listOf("series"), result.series.cards.map { it.candidate.gameId })
        assertEquals(listOf("popular"), result.popular.cards.map { it.candidate.gameId })
        assertEquals(listOf("On your waitlist", "From Series A"), result.waitlist.cards.last().reasons)
        assertEquals(listOf("From Series B", "Popular on ITAD"), result.series.cards.single().reasons)
    }

    @Test
    fun caps_after_sort_and_dedup_so_overflow_can_appear_in_a_later_section() = runTest {
        val candidates = FakeCandidates()
        candidates.wait = WinterSaleRawSection((1..31).map { raw("game-$it") })
        val series = FakeSeries(WinterSaleSeriesRawSection(listOf(WinterSaleSeriesRawCandidate(raw("game-31"), listOf("Series"))),
            checking = false, unavailable = false, moreSeriesFollowed = false))
        val offers = FakeOffers((1..31).associate { "game-$it" to offer("game-$it", 32 - it) })

        val result = WinterSaleGuideRepositoryImpl(candidates, series, offers, TestingLoggingListener()).load("GB", true, 1L, true)

        assertEquals(30, result.waitlist.cards.size)
        assertEquals("game-1", result.waitlist.cards.first().candidate.gameId)
        assertEquals(listOf("game-31"), result.series.cards.map { it.candidate.gameId })
    }

    @Test
    fun adult_off_recheck_hides_a_game_if_any_source_has_unknown_classification() = runTest {
        val candidates = FakeCandidates()
        candidates.wait = WinterSaleRawSection(listOf(raw("shared", adult = false)))
        val series = FakeSeries(WinterSaleSeriesRawSection(listOf(WinterSaleSeriesRawCandidate(raw("shared", adult = null), listOf("Series"))),
            checking = false, unavailable = false, moreSeriesFollowed = false))
        val offers = FakeOffers(mapOf("shared" to offer("shared", 20)))

        val result = WinterSaleGuideRepositoryImpl(candidates, series, offers, TestingLoggingListener()).load("GB", true, 1L, true)

        assertEquals(1, result.waitlist.cards.size)
        assertFalse(result.waitlist.cards.single().adultAllowedOff)
        assertTrue(result.forAdultSetting(false).waitlist.cards.isEmpty())
    }

    @Test
    fun keeps_section_failure_and_signed_out_states_without_hiding_popular() = runTest {
        val candidates = FakeCandidates()
        candidates.wait = WinterSaleRawSection(emptyList(), signInRequired = true)
        candidates.popular = WinterSaleRawSection(listOf(raw("popular")), shortFeed = true)
        val series = FakeSeries(WinterSaleSeriesRawSection(emptyList(), checking = true, unavailable = true, moreSeriesFollowed = false))
        val offers = FakeOffers(mapOf("popular" to offer("popular", 20)))

        val result = WinterSaleGuideRepositoryImpl(candidates, series, offers, TestingLoggingListener()).load("US", false, 1L, false)

        assertTrue(result.waitlist.signInRequired)
        assertTrue(result.series.checking)
        assertTrue(result.series.unavailable)
        assertTrue(result.popular.shortFeed)
        assertEquals(listOf("popular"), result.popular.cards.map { it.candidate.gameId })
    }

    @Test
    fun historical_lows_lead_then_discount_and_equal_cards_keep_one_order_whatever_the_response_order() = runTest {
        val ids = listOf("delta", "alpha", "low", "charlie", "big")
        val offers = mapOf(
            "delta" to offer("delta", 20), "alpha" to offer("alpha", 20), "charlie" to offer("charlie", 20),
            "big" to offer("big", 80), "low" to offer("low", 5).copy(historicalLow = WinterSaleMoney(10.0, "GBP")),
        )
        val orders = listOf(ids, ids.reversed()).map { order ->
            val candidates = FakeCandidates().apply { wait = WinterSaleRawSection(order.map { raw(it) }) }
            val series = FakeSeries(WinterSaleSeriesRawSection(emptyList(), checking = false, unavailable = false, moreSeriesFollowed = false))
            WinterSaleGuideRepositoryImpl(candidates, series, FakeOffers(offers), TestingLoggingListener()).load("GB", false, 1L, true).waitlist.cards.map { it.candidate.gameId }
        }

        assertEquals(listOf("low", "big", "alpha", "charlie", "delta"), orders.first())
        assertEquals(orders.first(), orders.last())
    }

    private class FakeCandidates : WinterSaleCandidatesRepository {
        var wait = WinterSaleRawSection(emptyList())
        var popular = WinterSaleRawSection(emptyList())
        override suspend fun waitlistCandidates(country: String, includeAdult: Boolean, saleStartEpochMs: Long?, saleActive: Boolean) = wait
        override suspend fun popularCandidates(country: String, includeAdult: Boolean) = popular
        override suspend fun waitlist(country: String, includeAdult: Boolean, saleStartEpochMs: Long?, saleActive: Boolean): WinterSaleCandidateSection = error("unused")
        override suspend fun popular(country: String, includeAdult: Boolean): WinterSaleCandidateSection = error("unused")
    }

    private class FakeSeries(var result: WinterSaleSeriesRawSection) : WinterSaleSeriesRepository {
        override suspend fun candidates(includeAdult: Boolean) = result
        override suspend fun load(country: String, includeAdult: Boolean): WinterSaleSeriesSection = error("unused")
    }

    private class FakeOffers(val offers: Map<String, WinterSaleOffer>) : WinterSaleOffersRepository {
        val calls = mutableListOf<List<String>>()
        override suspend fun load(gameIds: List<String>, country: String): WinterSaleOfferLoad {
            calls += gameIds
            return WinterSaleOfferLoad(country, 99L, gameIds.associateWith { id ->
                offers[id]?.let { WinterSalePriceStatus.Priced(it.copy(country = country)) } ?: WinterSalePriceStatus.NoCurrentOffer
            })
        }
    }

    private fun raw(id: String, adult: Boolean? = false) = WinterSaleRawCandidate(id, id, GameArtwork(), adult)
    private fun offer(id: String, cut: Int) = WinterSaleOffer(id, "GB", 1, "Shop", WinterSaleMoney(10.0, "GBP"), null, cut,
        "https://example.com/$id", null, emptyList(), null, null, null)
}
