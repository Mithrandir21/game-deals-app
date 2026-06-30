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
    fun toEpochDay_floors_to_whole_UTC_days() {
        assertEquals(0, 0L.toEpochDay())
        assertEquals(1, (MILLIS_PER_DAY + 1).toEpochDay())
        assertEquals(10, (10 * MILLIS_PER_DAY + 500).toEpochDay())
    }

    @Test
    fun empty_input_yields_empty_window() {
        assertTrue(windowedPriceHistory(emptyList(), PriceHistoryRange.ALL, now).isEmpty())
    }

    @Test
    fun ALL_keeps_every_distinct_day_and_extends_a_flat_segment_to_now() {
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
    fun multiple_changes_in_a_day_collapse_to_the_latest() {
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
    fun bounded_range_carries_the_prevailing_price_forward_to_the_window_start() {
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
    fun bounded_range_anchors_then_includes_in_window_changes() {
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
    fun ALL_coarsens_a_dense_daily_history_into_far_fewer_points() {
        // 120 consecutive daily points ending recently; ALL buckets at ~30 days.
        val dense = (0 until 120).map { pt(880 + it, 50.0 - it * 0.1) }
        val result = windowedPriceHistory(dense, PriceHistoryRange.ALL, now)

        // 120 daily points would be far denser; monthly bucketing collapses them dramatically.
        assertTrue(result.size < 20, "expected a coarsened series, got ${result.size} points")
        // Every plotted point is a real day (no fabricated timestamps), chronological & strictly increasing.
        val days = result.map { it.timestampEpochMs.toEpochDay() }
        assertEquals(days.sorted(), days)
        assertEquals(days.toSet().size, days.size)
    }

    @Test
    fun ALL_downsampling_keeps_the_all_time_low_even_as_a_single_day_dip() {
        // A flat $50 history with one deep one-day dip to $5 buried mid-bucket.
        val points = (0 until 120).map { pt(880 + it, 50.0) }.toMutableList()
        points[40] = pt(920, 5.0) // a lone dip on day 920
        val result = windowedPriceHistory(points, PriceHistoryRange.ALL, now)

        assertEquals(5.0, lowestPoint(result)?.priceValue)
        assertTrue(result.any { it.timestampEpochMs.toEpochDay() == 920L && it.priceValue == 5.0 })
    }

    @Test
    fun ALL_downsampling_samples_the_prevailing_price_not_the_period_low() {
        // Flat $60 history with a transient mid-bucket sale to $40 that is NOT the all-time-low
        // (a deeper $10 low sits elsewhere). The prevailing-price sampling must keep the bucket's
        // end price ($60), so the $40 blip never appears — the past must not look cheaper than it was.
        val points = (0 until 120).map { pt(880 + it, 60.0) }.toMutableList()
        points[5] = pt(885, 40.0)   // transient sale inside bucket 0 (ends; price returns to $60)
        points[80] = pt(960, 10.0)  // the genuine all-time-low, much later
        val result = windowedPriceHistory(points, PriceHistoryRange.ALL, now)

        assertTrue(result.none { it.priceValue == 40.0 }, "transient non-low sale must not be plotted")
        assertEquals(10.0, lowestPoint(result)?.priceValue) // the real all-time-low is still kept
        assertTrue(result.any { it.priceValue == 60.0 }) // prevailing price is represented
    }

    @Test
    fun ALL_downsampling_preserves_the_true_latest_point() {
        val points = (0 until 120).map { pt(880 + it, 50.0 - it * 0.1) } // newest is the cheapest
        val result = windowedPriceHistory(points, PriceHistoryRange.ALL, now)

        // The real last change (day 999, the cheapest) is retained; the synthetic now@1000 carries its price.
        val lastReal = points.last()
        assertTrue(result.any { it.timestampEpochMs == lastReal.timestampEpochMs && it.priceValue == lastReal.priceValue })
        assertEquals(lastReal.priceValue, result.last().priceValue)
        assertEquals(1000, result.last().timestampEpochMs.toEpochDay())
    }

    @Test
    fun THREE_MONTHS_keeps_every_daily_point_no_downsampling() {
        // 80 consecutive daily points inside the 3-month window; bucketDays=1 is a no-op.
        val dense = (0 until 80).map { pt(920 + it, 40.0 - it * 0.1) }
        val result = windowedPriceHistory(dense, PriceHistoryRange.THREE_MONTHS, now)

        // All 80 distinct days survive (newest day 999 < now 1000, so a synthetic now point is added).
        assertEquals(81, result.size)
        assertEquals(1000, result.last().timestampEpochMs.toEpochDay())
    }

    @Test
    fun ONE_YEAR_coarsens_dense_daily_history_to_roughly_weekly() {
        // A dense year of daily points; 1Y buckets at ~7 days → on the order of 52 points.
        val dense = (0 until 360).map { pt(640 + it, 60.0 - it * 0.05) }
        val result = windowedPriceHistory(dense, PriceHistoryRange.ONE_YEAR, now)

        assertTrue(result.size in 40..70, "expected ~weekly granularity, got ${result.size} points")
    }

    @Test
    fun latestRegular_returns_the_most_recent_non_null_regular_price() {
        val points = listOf(
            pt(900, 60.0, regular = 60.0),
            pt(950, 30.0, regular = null),
            pt(980, 45.0, regular = 50.0),
        )
        assertEquals(50.0, latestRegular(points))
        assertNull(latestRegular(listOf(pt(900, 60.0))))
    }

    @Test
    fun lowestPoint_returns_the_cheapest_entry() {
        val points = listOf(pt(900, 60.0), pt(950, 12.0), pt(980, 30.0))
        assertEquals(12.0, lowestPoint(points)?.priceValue)
        assertNull(lowestPoint(emptyList()))
    }
}
