package pm.bam.gamedeals.feature.deals.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class PriceTiersTest {

    @Test
    fun dollar_like_currencies_use_the_base_steps() {
        assertEquals(listOf(5, 10, 20, 50), priceTiers("USD"))
        assertEquals(listOf(5, 10, 20, 50), priceTiers("EUR"))
        assertEquals(listOf(5, 10, 20, 50), priceTiers(null))
    }

    @Test
    fun other_currencies_scale_the_steps() {
        assertEquals(listOf(500, 1000, 2000, 5000), priceTiers("JPY"))
        assertEquals(listOf(200, 400, 800, 2000), priceTiers("INR"))
        assertEquals(listOf(50000, 100000, 200000, 500000), priceTiers("IDR"))
    }

    @Test
    fun steps_are_labelled_as_whole_prices() {
        assertEquals("$5", priceTierLabel(5, "USD"))
        assertEquals("¥500", priceTierLabel(500, "JPY"))
        assertEquals("₹200", priceTierLabel(200, "INR"))
        assertEquals("150 TRY", priceTierLabel(150, "TRY"))
        assertEquals("5", priceTierLabel(5, null))
    }
}
