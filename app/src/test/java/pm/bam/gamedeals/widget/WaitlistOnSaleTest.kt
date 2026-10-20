package pm.bam.gamedeals.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import pm.bam.gamedeals.domain.models.WaitlistDisplayItem

class WaitlistOnSaleTest {

    private fun item(id: String, cut: Int?, price: Double? = 1.0, low: Double? = 0.5) = WaitlistDisplayItem(
        gameId = id,
        title = id,
        bestPriceDenominated = price?.let { "$$it" },
        discountPercent = cut,
        bestPriceValue = price,
        historicalLowValue = low,
    )

    @Test
    fun keeps_only_discounted_games_with_a_price() {
        val items = listOf(item("full-price", cut = 0), item("no-deal", cut = null, price = null), item("sale", cut = 30))
        assertEquals(listOf("sale"), items.onSale().map { it.gameId })
    }

    @Test
    fun sorts_by_biggest_discount_then_all_time_lows_first() {
        val items = listOf(
            item("half", cut = 50),
            item("big", cut = 80),
            item("half-at-low", cut = 50, price = 1.0, low = 1.0),
        )
        assertEquals(listOf("big", "half-at-low", "half"), items.onSale().map { it.gameId })
    }
}
