package pm.bam.gamedeals.domain.repositories.sharedwishlist

import kotlin.test.assertTrue
import pm.bam.gamedeals.common.exceptions.isRateLimited
import pm.bam.gamedeals.common.exceptions.HttpStatusFailure
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.mokkery.verify.VerifyMode.Companion.exactly
import dev.mokkery.verifySuspend
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.domain.models.BundleGamePrice
import pm.bam.gamedeals.domain.models.GameArtwork
import pm.bam.gamedeals.domain.source.DealsSource
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.fixtures.game
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SharedWishlistRepositoryTest {

    private fun price(id: String, value: Double) = BundleGamePrice(
        gameId = id, bestShopName = "Steam", bestPriceValue = value, bestPriceDenominated = "$$value", bestCutPercent = 50,
        historicalLowValue = null, historicalLowDenominated = null,
    )

    private val priceCalls = mutableListOf<List<String>>()
    private val dealsSource: DealsSource = mock(MockMode.autoUnit) {
        everySuspend { fetchGame(any()) } calls { (id: String) -> game(gameID = id, title = "Title $id", thumb = "art-$id") }
        everySuspend { fetchBundleGamePrices(any()) } calls { (ids: List<String>) ->
            priceCalls += ids
            ids.filter { it != "b" }.map { price(it, 5.0) }
        }
    }

    private fun repository(source: DealsSource = dealsSource) = SharedWishlistRepositoryImpl(source, TestingLoggingListener())

    @Test
    fun loads_each_game_in_the_link_order_with_its_price_from_one_batched_call() = runTest {
        val list = repository().load(listOf("c", "a", "b", "a"))
        assertEquals(listOf("c", "a", "b"), list.games.map { it.gameId })
        assertEquals("Title c", list.games[0].title)
        assertEquals(GameArtwork(banner300 = "art-c"), list.games[0].artwork)
        assertEquals(5.0, list.games[0].price?.bestPriceValue)
        assertNull(list.games[2].price, "a game with no current deal has no price")
        assertEquals(listOf(listOf("c", "a", "b")), priceCalls, "one price call, repeats dropped")
        assertEquals(0, list.unavailableCount)
    }

    @Test
    fun a_game_that_cannot_be_read_is_left_out_and_counted() = runTest {
        everySuspend { dealsSource.fetchGame("a") } throws IllegalStateException("ITAD 500")
        everySuspend { dealsSource.fetchGame("b") } returns game(gameID = "b", title = " ")
        val list = repository().load(listOf("a", "b", "c"))
        assertEquals(listOf("c"), list.games.map { it.gameId })
        assertEquals(2, list.unavailableCount)
    }

    @Test
    fun prices_are_best_effort() = runTest {
        everySuspend { dealsSource.fetchBundleGamePrices(any()) } throws IllegalStateException("ITAD 429")
        val list = repository().load(listOf("a", "c"))
        assertEquals(listOf("a", "c"), list.games.map { it.gameId })
        assertEquals(listOf(null, null), list.games.map { it.price })
    }

    @Test
    fun a_full_link_of_ten_with_prices_rate_limited_shows_every_game_in_order_without_prices() = runTest {
        everySuspend { dealsSource.fetchBundleGamePrices(any()) } throws IllegalStateException("ITAD 429")
        val ids = (1..10).map { "g$it" }

        val list = repository().load(ids)

        assertEquals(ids, list.games.map { it.gameId })
        assertEquals(List(10) { null }, list.games.map { it.price })
        assertEquals(0, list.unavailableCount)
    }

    @Test
    fun throws_when_no_game_could_be_read() = runTest {
        everySuspend { dealsSource.fetchGame(any()) } throws IllegalStateException("offline")
        assertFailsWith<IllegalStateException> { repository().load(listOf("a", "b")) }
    }

    @Test
    fun when_itad_is_rate_limiting_every_game_the_failure_says_so() = runTest {
        // So the screen can say "IsThereAnyDeal is busy" instead of blaming the connection.
        everySuspend { dealsSource.fetchGame(any()) } throws object : RuntimeException("HTTP 429"), HttpStatusFailure { override val httpStatus = 429 }
        val error = assertFailsWith<IllegalStateException> { repository().load(listOf("a", "b")) }
        assertTrue(error.isRateLimited())
    }

    @Test
    fun an_empty_list_makes_no_calls() = runTest {
        assertEquals(SharedWishlist(emptyList()), repository().load(emptyList()))
        verifySuspend(exactly(0)) { dealsSource.fetchGame(any()) }
        verifySuspend(exactly(0)) { dealsSource.fetchBundleGamePrices(any()) }
    }
}
