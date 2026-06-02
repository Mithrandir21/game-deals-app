package pm.bam.gamedeals.common.ui.share

import kotlin.test.Test
import kotlin.test.assertEquals

class DefaultDealShareTextBuilderTest {

    private val builder: DealShareTextBuilder = DefaultDealShareTextBuilder()

    @Test
    fun build_contains_title_price_store_and_url() {
        val text = builder.build(
            gameTitle = "Hollow Knight",
            salePriceDenominated = "$7.49",
            storeName = "Steam",
            dealUrl = "https://www.cheapshark.com/redirect?dealID=abc123",
        )

        assertEquals(
            "Hollow Knight — \$7.49 at Steam: https://www.cheapshark.com/redirect?dealID=abc123",
            text,
        )
    }
}
