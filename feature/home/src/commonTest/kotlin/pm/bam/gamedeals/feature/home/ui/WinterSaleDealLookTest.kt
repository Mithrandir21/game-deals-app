package pm.bam.gamedeals.feature.home.ui

import pm.bam.gamedeals.common.ui.components.DealLowFlag
import pm.bam.gamedeals.common.ui.components.dealLowFlag
import pm.bam.gamedeals.domain.models.GameArtwork
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleDeal
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleDealFlag
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleMoney
import pm.bam.gamedeals.domain.utils.formatMoney
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** How a sale deal reads in the shared item look: which one letter it shows, and when its old price is struck through. */
class WinterSaleDealLookTest {

    private fun deal(flag: WinterSaleDealFlag? = null, price: Double = 1.99, regular: Double? = 9.99) = WinterSaleDeal(
        gameId = "g", title = "Game", artwork = GameArtwork(), shopId = 61, shopName = "Steam",
        price = WinterSaleMoney(price, "GBP"), regular = regular?.let { WinterSaleMoney(it, "GBP") }, cutPercent = 80,
        url = "https://itad.link/g", voucher = null, flag = flag, mature = false,
    )

    private fun WinterSaleDeal.letter(): DealLowFlag? =
        dealLowFlag(isNewHistoricalLow = isNewLow, isLowestEver = isLowestEver, isStoreLow = isStoreLow)

    @Test
    fun each_ITAD_flag_shows_its_own_letter() {
        assertEquals(DealLowFlag.NEW_LOW, deal(WinterSaleDealFlag.NewLow).letter())
        assertEquals(DealLowFlag.ALL_TIME_LOW, deal(WinterSaleDealFlag.AllTimeLow).letter())
        assertEquals(DealLowFlag.STORE_LOW, deal(WinterSaleDealFlag.StoreLow).letter())
        assertNull(deal(null).letter())
    }

    @Test
    fun a_new_low_also_counts_as_a_lowest_ever_price() {
        // The same reading as Home's deals, where ITAD's "N" sets both flags.
        val newLow = deal(WinterSaleDealFlag.NewLow)
        assertEquals(true, newLow.isNewLow)
        assertEquals(true, newLow.isLowestEver)
        assertEquals(false, newLow.isStoreLow)
        assertEquals(false, deal(WinterSaleDealFlag.StoreLow).isLowestEver)
    }

    @Test
    fun the_old_price_shows_only_when_it_is_above_the_sale_price() {
        assertEquals(formatMoney(9.99, "GBP"), deal(price = 1.99, regular = 9.99).regularPriceText())
        assertNull(deal(price = 1.99, regular = 1.99).regularPriceText())
        assertNull(deal(price = 1.99, regular = 0.99).regularPriceText())
        assertNull(deal(price = 1.99, regular = null).regularPriceText())
    }
}
