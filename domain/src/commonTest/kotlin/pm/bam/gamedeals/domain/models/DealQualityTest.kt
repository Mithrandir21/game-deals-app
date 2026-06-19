package pm.bam.gamedeals.domain.models

import kotlinx.collections.immutable.persistentListOf
import pm.bam.gamedeals.testing.fixtures.gameDeal
import pm.bam.gamedeals.testing.fixtures.gameDetails
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DealQualityTest {

    private fun details(low: Double, vararg dealPrices: Double) = gameDetails(
        cheapestPriceEver = GameDetails.GameCheapestPriceEver(priceValue = low, priceDenominated = "$$low", date = ""),
        deals = dealPrices.mapIndexed { i, p -> gameDeal(dealID = "d$i", priceValue = p, priceDenominated = "$$p") }.let { persistentListOf(*it.toTypedArray()) },
    )

    @Test
    fun `no deals returns null`() {
        assertNull(details(low = 5.0).dealQuality())
    }

    @Test
    fun `non-positive best price returns null`() {
        assertNull(details(low = 5.0, dealPrices = doubleArrayOf(0.0)).dealQuality())
    }

    @Test
    fun `no recorded low returns null`() {
        assertNull(details(low = 0.0, dealPrices = doubleArrayOf(9.99)).dealQuality())
    }

    @Test
    fun `best price equal to all-time low is AllTimeLow tier with zero percent`() {
        val quality = details(low = 9.99, dealPrices = doubleArrayOf(9.99)).dealQuality()
        assertEquals(DealQuality.Tier.AllTimeLow, quality?.tier)
        assertEquals(0, quality?.percentAboveLow)
    }

    @Test
    fun `best price below all-time low is AllTimeLow tier`() {
        // The cheapest of multiple deals is used, even when others are pricier.
        val quality = details(low = 10.0, dealPrices = doubleArrayOf(8.0, 25.0)).dealQuality()
        assertEquals(DealQuality.Tier.AllTimeLow, quality?.tier)
    }

    @Test
    fun `within ten percent of the low is NearLow`() {
        val quality = details(low = 10.0, dealPrices = doubleArrayOf(10.5)).dealQuality()
        assertEquals(DealQuality.Tier.NearLow, quality?.tier)
        assertEquals(5, quality?.percentAboveLow)
    }

    @Test
    fun `well above the low is Elevated with rounded percent`() {
        val quality = details(low = 10.0, dealPrices = doubleArrayOf(15.0)).dealQuality()
        assertEquals(DealQuality.Tier.Elevated, quality?.tier)
        assertEquals(50, quality?.percentAboveLow)
        assertEquals("$10.0", quality?.allTimeLowDenominated)
    }
}
