package pm.bam.gamedeals.common.ui.components

import kotlin.test.Test
import kotlin.test.assertEquals

class DiscountBadgeTest {

    @Test
    fun `large cuts map to HIGH tier`() {
        assertEquals(DiscountTier.HIGH, discountTier(50))
        assertEquals(DiscountTier.HIGH, discountTier(78))
        assertEquals(DiscountTier.HIGH, discountTier(100))
    }

    @Test
    fun `mid cuts map to MEDIUM tier`() {
        assertEquals(DiscountTier.MEDIUM, discountTier(25))
        assertEquals(DiscountTier.MEDIUM, discountTier(37))
        assertEquals(DiscountTier.MEDIUM, discountTier(49))
    }

    @Test
    fun `small cuts map to LOW tier`() {
        assertEquals(DiscountTier.LOW, discountTier(1))
        assertEquals(DiscountTier.LOW, discountTier(24))
    }
}
