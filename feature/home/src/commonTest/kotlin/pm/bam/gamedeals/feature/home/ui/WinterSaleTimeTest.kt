package pm.bam.gamedeals.feature.home.ui

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class WinterSaleTimeTest {
    private val saleStart = 1_797_530_400_000L // 17 December 2026, 18:00 UTC

    @Test
    fun shows_the_sale_start_in_the_phone_time_zone_with_its_date() {
        assertEquals("17 Dec 2026, 18:00", winterSaleTime(saleStart, TimeZone.UTC))
        assertEquals("17 Dec 2026, 10:00", winterSaleTime(saleStart, TimeZone.of("America/Los_Angeles")))
        assertEquals("18 Dec 2026, 03:00", winterSaleTime(saleStart, TimeZone.of("Asia/Tokyo")))
    }
}
