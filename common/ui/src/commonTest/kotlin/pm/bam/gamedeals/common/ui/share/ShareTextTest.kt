package pm.bam.gamedeals.common.ui.share

import kotlin.test.Test
import kotlin.test.assertEquals

class ShareTextTest {

    @Test
    fun share_text_contains_title_price_store_and_redirect_url() {
        val text = buildDealShareText(
            gameTitle = "Hollow Knight",
            salePriceDenominated = "$7.49",
            storeName = "Steam",
            dealId = "abc123",
        )

        assertEquals(
            "Hollow Knight — \$7.49 at Steam: https://www.cheapshark.com/redirect?dealID=abc123",
            text,
        )
    }
}
