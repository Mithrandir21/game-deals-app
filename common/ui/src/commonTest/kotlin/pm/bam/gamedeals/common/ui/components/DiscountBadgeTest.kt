package pm.bam.gamedeals.common.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class DiscountBadgeTest {

    @Test
    fun cuts_of_75_percent_or_more_map_to_the_TOP_tier() {
        assertEquals(DiscountTier.TOP, discountTier(75))
        assertEquals(DiscountTier.TOP, discountTier(90))
        assertEquals(DiscountTier.TOP, discountTier(100))
    }

    @Test
    fun large_cuts_below_75_map_to_HIGH_tier() {
        assertEquals(DiscountTier.HIGH, discountTier(50))
        assertEquals(DiscountTier.HIGH, discountTier(74))
    }

    @Test
    fun mid_cuts_map_to_MEDIUM_tier() {
        assertEquals(DiscountTier.MEDIUM, discountTier(25))
        assertEquals(DiscountTier.MEDIUM, discountTier(37))
        assertEquals(DiscountTier.MEDIUM, discountTier(49))
    }

    @Test
    fun small_cuts_map_to_LOW_tier() {
        assertEquals(DiscountTier.LOW, discountTier(1))
        assertEquals(DiscountTier.LOW, discountTier(24))
    }
}
