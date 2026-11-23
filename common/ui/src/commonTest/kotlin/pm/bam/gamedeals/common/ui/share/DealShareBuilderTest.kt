package pm.bam.gamedeals.common.ui.share

import pm.bam.gamedeals.domain.models.GameDetails
import pm.bam.gamedeals.logging.featureflags.FeatureFlag
import pm.bam.gamedeals.logging.featureflags.FeatureFlags
import pm.bam.gamedeals.logging.featureflags.NoOpFeatureFlags
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DefaultDealShareBuilderTest {

    private val hollowKnight = "018d937f-1ae9-734c-ba47-bd357cf07edd"

    private val deal = GameDetails.GameDeal(
        storeID = 61,
        dealID = "deal-1",
        priceValue = 7.49,
        priceDenominated = "$7.49",
        retailPriceValue = 14.99,
        retailPriceDenominated = "$14.99",
        savings = 50,
        url = "https://itad.link/abc/",
        isLowestEver = true,
        expiryEpochMs = 1_797_786_000_000,
    )

    // The flag ships on, so the built-in values are "on".
    private val linksOn: FeatureFlags = NoOpFeatureFlags
    private val linksOff = object : FeatureFlags by NoOpFeatureFlags {
        override fun isEnabled(flag: FeatureFlag) = flag != FeatureFlag.DealLinks && NoOpFeatureFlags.isEnabled(flag)
    }

    private fun build(flags: FeatureFlags, gameId: String = hollowKnight) =
        DefaultDealShareBuilder(flags).build(
            gameId = gameId,
            gameTitle = "Hollow Knight",
            coverUrl = "https://assets/banner600.jpg",
            storeName = "Steam",
            deal = deal,
        )

    @Test
    fun with_deal_links_off_the_text_carries_the_store_link_and_there_is_no_card() {
        val share = build(linksOff)
        assertEquals("Hollow Knight — \$7.49 at Steam: https://itad.link/abc/", share.text)
        assertNull(share.card)
    }

    @Test
    fun with_deal_links_on_the_text_carries_the_game_link_and_the_card_shows_the_deal() {
        val share = build(linksOn)
        val link = "https://gamedeals.bam.pm/g/$hollowKnight"
        assertEquals("Hollow Knight — \$7.49 at Steam: $link", share.text)
        val card = assertNotNull(share.card)
        assertEquals(
            DealShareCard(
                title = "Hollow Knight",
                coverUrl = "https://assets/banner600.jpg",
                priceDenominated = "$7.49",
                regularDenominated = "$14.99",
                cutPercent = 50,
                storeName = "Steam",
                isNewHistoricalLow = false,
                isLowestEver = true,
                isStoreLow = false,
                endsAtEpochMs = 1_797_786_000_000,
                link = link,
            ),
            card,
        )
    }

    @Test
    fun a_game_id_that_cannot_make_a_link_falls_back_to_the_store_link() {
        val share = build(linksOn, gameId = "not-an-itad-id")
        assertEquals("Hollow Knight — \$7.49 at Steam: https://itad.link/abc/", share.text)
        assertNull(share.card)
    }
}
