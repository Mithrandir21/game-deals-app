package pm.bam.gamedeals.domain.models

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** When a waitlisted game counts as "at its lowest price" (the H on its row, and the "Now at lowest" sort). */
class WaitlistAtLowestTest {

    private fun item(price: Double?, low: Double?, cut: Int?) = WaitlistDisplayItem(
        gameId = "g",
        title = "Game",
        bestPriceDenominated = price?.let { "$$it" },
        discountPercent = cut,
        bestPriceValue = price,
        historicalLowValue = low,
    )

    @Test
    fun a_price_at_or_below_the_all_time_low_counts() {
        assertTrue(item(price = 4.99, low = 4.99, cut = 75).isAtHistoricalLow)
        assertTrue(item(price = 3.99, low = 4.99, cut = 80).isAtHistoricalLow)
        assertFalse(item(price = 5.99, low = 4.99, cut = 70).isAtHistoricalLow)
        assertFalse(item(price = null, low = 4.99, cut = null).isAtHistoricalLow)
    }

    @Test
    fun a_game_that_is_simply_free_does_not_count() {
        assertFalse(item(price = 0.0, low = 0.0, cut = 0).isAtHistoricalLow)
        assertFalse(item(price = 0.0, low = 0.0, cut = null).isAtHistoricalLow)
        // Free for a while (a 100% cut) is a real deal at its lowest.
        assertTrue(item(price = 0.0, low = 0.0, cut = 100).isAtHistoricalLow)
        // A full-price game that has never been cheaper still counts.
        assertTrue(item(price = 19.99, low = 19.99, cut = 0).isAtHistoricalLow)
    }
}
