package pm.bam.gamedeals.common.ui.deal

import pm.bam.gamedeals.common.ui.components.DealLowFlag
import pm.bam.gamedeals.domain.models.GameDetails
import kotlin.test.Test
import kotlin.test.assertEquals

class PeekLowFlagsTest {

    private fun deal(newLow: Boolean = false, lowestEver: Boolean = false, storeLow: Boolean = false) = GameDetails.GameDeal(
        storeID = 1, dealID = "d", priceValue = 1.0, priceDenominated = "$1", retailPriceValue = 2.0, retailPriceDenominated = "$2",
        savings = 50, isNewHistoricalLow = newLow, isLowestEver = lowestEver, isStoreLow = storeLow,
    )

    @Test
    fun the_best_deal_letter_comes_first_then_each_other_letter_once() {
        val flags = peekLowFlags(
            best = deal(storeLow = true),
            others = listOf(deal(lowestEver = true), deal(storeLow = true), deal(newLow = true, lowestEver = true)),
        )

        assertEquals(listOf(DealLowFlag.STORE_LOW, DealLowFlag.ALL_TIME_LOW, DealLowFlag.NEW_LOW), flags)
    }

    @Test
    fun no_letters_means_no_explanation() {
        assertEquals(emptyList(), peekLowFlags(best = deal(), others = listOf(deal())))
        assertEquals(emptyList(), peekLowFlags(best = null, others = emptyList()))
    }
}
