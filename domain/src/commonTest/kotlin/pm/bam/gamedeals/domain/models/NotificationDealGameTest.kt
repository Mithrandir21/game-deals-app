package pm.bam.gamedeals.domain.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NotificationDealGameTest {

    private fun deal(shop: String, price: Double) =
        NotificationShopDeal(
            shopName = shop,
            salePriceValue = price,
            salePriceDenominated = "$price",
            regularPriceDenominated = null,
            cutPercent = 50,
            url = "u",
        )

    private fun game(id: String, title: String = "t$id", deals: List<NotificationShopDeal>) =
        NotificationDealGame(gameId = id, title = title, deals = deals)

    @Test
    fun same_game_across_drops_collapses_to_one_entry_with_merged_deals() {
        val merged = listOf(
            game("g1", deals = listOf(deal("GOG", 25.0))),
            game("g1", deals = listOf(deal("Steam", 19.99))),
            game("g1", deals = listOf(deal("Fanatical", 22.0), deal("GOG", 17.09))),
        ).mergedByGameId()

        assertEquals(1, merged.size)
        assertEquals(listOf("GOG", "Steam", "Fanatical", "GOG"), merged.single().deals.map { it.shopName })
    }

    @Test
    fun bestDeal_after_merge_is_the_globally_cheapest_even_when_in_a_later_drop() {
        val merged = listOf(
            game("g1", deals = listOf(deal("GOG", 25.0))),
            game("g1", deals = listOf(deal("Steam", 19.99))),
            game("g1", deals = listOf(deal("GOG", 17.09))), // cheapest sits in the last drop
        ).mergedByGameId()

        assertEquals("GOG", merged.single().bestDeal?.shopName)
        assertEquals(17.09, merged.single().bestDeal?.salePriceValue)
    }

    @Test
    fun distinct_games_are_preserved_in_first_seen_order() {
        val merged = listOf(
            game("g2", deals = listOf(deal("GOG", 5.0))),
            game("g1", deals = listOf(deal("Steam", 9.99))),
            game("g2", deals = listOf(deal("Steam", 4.0))),
        ).mergedByGameId()

        assertEquals(listOf("g2", "g1"), merged.map { it.gameId })
    }

    @Test
    fun a_game_with_only_empty_drops_stays_expired() {
        val merged = listOf(
            game("g1", deals = emptyList()),
            game("g1", deals = emptyList()),
        ).mergedByGameId()

        assertTrue(merged.single().isExpired)
    }
}
