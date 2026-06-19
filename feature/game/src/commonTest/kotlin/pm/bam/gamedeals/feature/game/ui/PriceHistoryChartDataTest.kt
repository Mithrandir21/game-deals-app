package pm.bam.gamedeals.feature.game.ui

import pm.bam.gamedeals.domain.models.PriceHistory.PricePoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PriceHistoryChartDataTest {

    private fun pt(
        day: Int,
        price: Double,
        regular: Double? = null,
        cut: Int = 0,
        shop: String? = null,
    ) = PricePoint(
        timestampEpochMs = day * MILLIS_PER_DAY,
        priceValue = price,
        priceDenominated = "$$price",
        cutPercent = cut,
        regularValue = regular,
        shopName = shop,
    )

    private val now = 1000 * MILLIS_PER_DAY

    @Test
    fun `toEpochDay floors to whole UTC days`() {
        assertEquals(0, 0L.toEpochDay())
        assertEquals(1, (MILLIS_PER_DAY + 1).toEpochDay())
        assertEquals(10, (10 * MILLIS_PER_DAY + 500).toEpochDay())
    }

    @Test
    fun `empty input yields empty window`() {
        assertTrue(windowedPriceHistory(emptyList(), PriceHistoryRange.ALL, now).isEmpty())
    }

    @Test
    fun `ALL keeps every distinct day and extends a flat segment to now`() {
        val result = windowedPriceHistory(
            listOf(pt(900, 60.0), pt(950, 30.0)),
            PriceHistoryRange.ALL,
            now,
        )

        assertEquals(3, result.size)
        assertEquals(60.0, result.first().priceValue)
        // Synthetic "now" point carries the latest price at today's day.
        assertEquals(30.0, result.last().priceValue)
        assertEquals(1000, result.last().timestampEpochMs.toEpochDay())
    }

    @Test
    fun `multiple changes in a day collapse to the latest`() {
        val result = windowedPriceHistory(
            listOf(pt(900, 60.0), pt(900, 55.0), pt(950, 30.0)),
            PriceHistoryRange.ALL,
            now,
        )

        // Day 900 collapses to its last value (55), so days are 900, 950, and the synthetic now.
        assertEquals(3, result.size)
        assertEquals(55.0, result.first().priceValue)
    }

    @Test
    fun `bounded range carries the prevailing price forward to the window start`() {
        // Both changes predate the 3-month window; the window must still show the held price.
        val result = windowedPriceHistory(
            listOf(pt(800, 60.0), pt(850, 40.0)),
            PriceHistoryRange.THREE_MONTHS,
            now,
        )

        val cutoffDay = (1000 - 90).toLong()
        assertEquals(2, result.size)
        assertEquals(cutoffDay, result.first().timestampEpochMs.toEpochDay())
        assertTrue(result.all { it.priceValue == 40.0 })
        assertEquals(1000, result.last().timestampEpochMs.toEpochDay())
    }

    @Test
    fun `bounded range anchors then includes in-window changes`() {
        val result = windowedPriceHistory(
            listOf(pt(800, 60.0), pt(950, 20.0)),
            PriceHistoryRange.THREE_MONTHS,
            now,
        )

        // anchor@cutoff(910)=60, real change@950=20, synthetic now@1000=20
        assertEquals(3, result.size)
        assertEquals(60.0, result.first().priceValue)
        assertEquals(910, result.first().timestampEpochMs.toEpochDay())
        assertEquals(20.0, result[1].priceValue)
        assertEquals(20.0, result.last().priceValue)
    }

    @Test
    fun `latestRegular returns the most recent non-null regular price`() {
        val points = listOf(
            pt(900, 60.0, regular = 60.0),
            pt(950, 30.0, regular = null),
            pt(980, 45.0, regular = 50.0),
        )
        assertEquals(50.0, latestRegular(points))
        assertNull(latestRegular(listOf(pt(900, 60.0))))
    }

    @Test
    fun `lowestPoint returns the cheapest entry`() {
        val points = listOf(pt(900, 60.0), pt(950, 12.0), pt(980, 30.0))
        assertEquals(12.0, lowestPoint(points)?.priceValue)
        assertNull(lowestPoint(emptyList()))
    }
}
