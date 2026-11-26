package pm.bam.gamedeals.domain.repositories.wintersale

import pm.bam.gamedeals.logging.LogLevel
import pm.bam.gamedeals.testing.RecordingLogger
import pm.bam.gamedeals.testing.TestingLoggingListener
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.test.runTest
import pm.bam.gamedeals.common.time.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class WinterSaleOffersRepositoryTest {
    @Test
    fun deduplicates_and_chunks_four_hundred_thirty_ids_without_exceeding_the_api_cap() = runTest {
        val source = FakeOfferSource()
        val ids = (1..430).map { "game-$it" }

        val result = WinterSaleOffersRepositoryImpl(source, Clock { 123L }, TestingLoggingListener()).load(ids + "game-1" + "", "GB")

        assertEquals(listOf(200, 200, 30), source.calls.map { it.first.size })
        assertTrue(source.calls.all { it.second == "GB" })
        assertEquals(430, result.perGame.size)
        assertEquals("GB", result.country)
        assertEquals(123L, result.refreshedAtEpochMillis)
    }

    @Test
    fun one_failed_chunk_does_not_hide_successful_chunks() = runTest {
        val source = FakeOfferSource(failOnId = "game-201")
        val logger = RecordingLogger()
        val result = WinterSaleOffersRepositoryImpl(source, Clock { 0L }, logger).load((1..430).map { "game-$it" }, "GB")

        assertEquals(1, logger.at(LogLevel.ERROR).size)
        assertIs<WinterSalePriceStatus.Priced>(result.perGame["game-1"])
        assertEquals(WinterSalePriceStatus.Unavailable, result.perGame["game-201"])
        assertEquals(WinterSalePriceStatus.Unavailable, result.perGame["game-400"])
        assertIs<WinterSalePriceStatus.Priced>(result.perGame["game-401"])
    }

    @Test
    fun a_game_left_out_of_a_successful_reply_has_no_current_offer() = runTest {
        // ITAD's overview omits games no store sells any more (e.g. delisted GTA classics); that is not a failure.
        val source = object : WinterSaleOfferSource {
            override suspend fun fetch(gameIds: List<String>, country: String): Map<String, WinterSaleOffer?> =
                mapOf("empty" to null, "priced" to offer("priced"))
        }
        val result = WinterSaleOffersRepositoryImpl(source, Clock { 0L }, TestingLoggingListener()).load(listOf("empty", "missing", "priced"), "GB")

        assertEquals(WinterSalePriceStatus.NoCurrentOffer, result.perGame["empty"])
        assertEquals(WinterSalePriceStatus.NoCurrentOffer, result.perGame["missing"])
        assertIs<WinterSalePriceStatus.Priced>(result.perGame["priced"])
        assertFalse(result.perGame.values.any { it == WinterSalePriceStatus.Unavailable })
    }

    @Test
    fun mismatched_region_cannot_be_reused() = runTest {
        val source = object : WinterSaleOfferSource {
            override suspend fun fetch(gameIds: List<String>, country: String): Map<String, WinterSaleOffer?> =
                mapOf("game" to offer("game", country = "US"))
        }
        val result = WinterSaleOffersRepositoryImpl(source, Clock { 0L }, TestingLoggingListener()).load(listOf("game"), "GB")

        assertEquals(WinterSalePriceStatus.Unavailable, result.perGame["game"])
    }

    @Test
    fun historical_low_comparison_requires_the_same_currency() {
        assertTrue(offer("game", historicalLow = WinterSaleMoney(10.0, "GBP")).isAtHistoricalLow)
        assertFalse(offer("game", historicalLow = WinterSaleMoney(10.0, "USD")).isAtHistoricalLow)
        // A game that is simply free (no price, no discount) is never "at its lowest price"; a 100% cut still is.
        val free = offer("game", cut = 0, historicalLow = WinterSaleMoney(0.0, "GBP")).copy(price = WinterSaleMoney(0.0, "GBP"))
        assertFalse(free.isAtHistoricalLow)
        assertTrue(free.copy(cutPercent = 100).isAtHistoricalLow)
        assertTrue(offer("game", cut = 1).isDiscounted)
        assertFalse(offer("game", cut = 0).isDiscounted)
    }

    @Test
    fun cancellation_stops_the_refresh() = runTest {
        val source = object : WinterSaleOfferSource {
            override suspend fun fetch(gameIds: List<String>, country: String): Map<String, WinterSaleOffer?> = throw CancellationException("cancelled")
        }
        assertFailsWith<CancellationException> { WinterSaleOffersRepositoryImpl(source, Clock { 0L }, TestingLoggingListener()).load(listOf("game"), "GB") }
    }
}

private class FakeOfferSource(private val failOnId: String? = null) : WinterSaleOfferSource {
    val calls = mutableListOf<Pair<List<String>, String>>()

    override suspend fun fetch(gameIds: List<String>, country: String): Map<String, WinterSaleOffer?> {
        calls += gameIds to country
        if (failOnId != null && failOnId in gameIds) error("chunk failed")
        return gameIds.associateWith { offer(it, country) }
    }
}

private fun offer(
    id: String,
    country: String = "GB",
    cut: Int = 20,
    historicalLow: WinterSaleMoney? = null,
) = WinterSaleOffer(
    gameId = id,
    country = country,
    shopId = 1,
    shopName = "Example Shop",
    price = WinterSaleMoney(10.0, "GBP"),
    regular = WinterSaleMoney(12.5, "GBP"),
    cutPercent = cut,
    url = "https://example.com/game",
    voucher = null,
    activationLabels = listOf("Steam"),
    checkedAtEpochMillis = null,
    expiresAtEpochMillis = null,
    historicalLow = historicalLow,
)
