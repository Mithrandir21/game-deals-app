package pm.bam.gamedeals.feature.deals.ui

import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.testing.fixtures.deal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GroupedSearchResultTest {

    @Test
    fun empty_input_produces_empty_output() {
        assertTrue(emptyList<Deal>().groupByGame().isEmpty())
    }

    @Test
    fun single_deal_passes_through_as_one_group_of_one() {
        val onlyDeal = deal(dealID = "a", gameID = "42", salePriceValue = 5.99)
        val grouped = listOf(onlyDeal).groupByGame()

        assertEquals(1, grouped.size)
        assertEquals("42", grouped[0].gameID)
        assertEquals(onlyDeal, grouped[0].cheapestDeal)
        assertEquals(1, grouped[0].totalDealCount)
    }

    @Test
    fun deals_with_same_gameID_collapse_to_a_single_group() {
        val deals = listOf(
            deal(dealID = "a", gameID = "1", salePriceValue = 5.00),
            deal(dealID = "b", gameID = "1", salePriceValue = 3.00),
            deal(dealID = "c", gameID = "1", salePriceValue = 4.00),
        )

        val grouped = deals.groupByGame()

        assertEquals(1, grouped.size)
        assertEquals(3, grouped[0].totalDealCount)
    }

    @Test
    fun cheapest_deal_is_selected_as_representative() {
        val cheapest = deal(dealID = "b", gameID = "1", salePriceValue = 3.00)
        val deals = listOf(
            deal(dealID = "a", gameID = "1", salePriceValue = 5.00),
            cheapest,
            deal(dealID = "c", gameID = "1", salePriceValue = 4.00),
        )

        val grouped = deals.groupByGame()

        assertEquals(cheapest, grouped[0].cheapestDeal)
    }

    @Test
    fun price_tie_is_broken_by_highest_dealRating() {
        val higherRated = deal(dealID = "b", gameID = "1", salePriceValue = 3.00, dealRating = 9.0)
        val deals = listOf(
            deal(dealID = "a", gameID = "1", salePriceValue = 3.00, dealRating = 7.5),
            higherRated,
            deal(dealID = "c", gameID = "1", salePriceValue = 3.00, dealRating = 8.0),
        )

        val grouped = deals.groupByGame()

        assertEquals(higherRated, grouped[0].cheapestDeal)
    }

    @Test
    fun groups_preserve_first_occurrence_order_from_source_list() {
        // Source order: game 1 appears at index 0 (best-rated 8.4), game 2 at index 3 (best-rated 7.0).
        // Group order must follow first-encounter, so game 1 stays ahead of game 2 — preserving the
        // API's DealRating-desc ordering at the group level.
        val deals = listOf(
            deal(dealID = "g1-a", gameID = "1", salePriceValue = 5.00, dealRating = 8.4),
            deal(dealID = "g1-b", gameID = "1", salePriceValue = 4.00, dealRating = 8.2),
            deal(dealID = "g1-c", gameID = "1", salePriceValue = 3.00, dealRating = 7.5),
            deal(dealID = "g2-a", gameID = "2", salePriceValue = 6.00, dealRating = 7.0),
            deal(dealID = "g2-b", gameID = "2", salePriceValue = 2.00, dealRating = 6.5),
        )

        val grouped = deals.groupByGame()

        assertEquals(listOf("1", "2"), grouped.map { it.gameID })
        // The representative of each group is still the cheapest, even though ordering is by first-seen.
        assertEquals("g1-c", grouped[0].cheapestDeal.dealID)
        assertEquals("g2-b", grouped[1].cheapestDeal.dealID)
    }
}
