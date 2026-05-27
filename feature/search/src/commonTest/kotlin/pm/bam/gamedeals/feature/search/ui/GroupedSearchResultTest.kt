package pm.bam.gamedeals.feature.search.ui

import pm.bam.gamedeals.testing.fixtures.deal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GroupedSearchResultTest {

    @Test
    fun empty_input_produces_empty_output() {
        assertTrue(emptyList<pm.bam.gamedeals.domain.models.Deal>().groupByGame().isEmpty())
    }

    @Test
    fun single_deal_passes_through_as_one_group_of_one() {
        val onlyDeal = deal(dealID = "a", gameID = 42, salePriceValue = 5.99)
        val grouped = listOf(onlyDeal).groupByGame()

        assertEquals(1, grouped.size)
        assertEquals(42, grouped[0].gameID)
        assertEquals(onlyDeal, grouped[0].cheapestDeal)
        assertEquals(1, grouped[0].totalDealCount)
    }

    @Test
    fun deals_with_same_gameID_collapse_to_a_single_group() {
        val deals = listOf(
            deal(dealID = "a", gameID = 1, salePriceValue = 5.00),
            deal(dealID = "b", gameID = 1, salePriceValue = 3.00),
            deal(dealID = "c", gameID = 1, salePriceValue = 4.00),
        )

        val grouped = deals.groupByGame()

        assertEquals(1, grouped.size)
        assertEquals(3, grouped[0].totalDealCount)
    }

    @Test
    fun cheapest_deal_is_selected_as_representative() {
        val cheapest = deal(dealID = "b", gameID = 1, salePriceValue = 3.00)
        val deals = listOf(
            deal(dealID = "a", gameID = 1, salePriceValue = 5.00),
            cheapest,
            deal(dealID = "c", gameID = 1, salePriceValue = 4.00),
        )

        val grouped = deals.groupByGame()

        assertEquals(cheapest, grouped[0].cheapestDeal)
    }

    @Test
    fun price_tie_is_broken_by_highest_dealRating() {
        val higherRated = deal(dealID = "b", gameID = 1, salePriceValue = 3.00, dealRating = 9.0)
        val deals = listOf(
            deal(dealID = "a", gameID = 1, salePriceValue = 3.00, dealRating = 7.5),
            higherRated,
            deal(dealID = "c", gameID = 1, salePriceValue = 3.00, dealRating = 8.0),
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
            deal(dealID = "g1-a", gameID = 1, salePriceValue = 5.00, dealRating = 8.4),
            deal(dealID = "g1-b", gameID = 1, salePriceValue = 4.00, dealRating = 8.2),
            deal(dealID = "g1-c", gameID = 1, salePriceValue = 3.00, dealRating = 7.5),
            deal(dealID = "g2-a", gameID = 2, salePriceValue = 6.00, dealRating = 7.0),
            deal(dealID = "g2-b", gameID = 2, salePriceValue = 2.00, dealRating = 6.5),
        )

        val grouped = deals.groupByGame()

        assertEquals(listOf(1, 2), grouped.map { it.gameID })
        // The representative of each group is still the cheapest, even though ordering is by first-seen.
        assertEquals("g1-c", grouped[0].cheapestDeal.dealID)
        assertEquals("g2-b", grouped[1].cheapestDeal.dealID)
    }

    @Test
    fun batman_search_fixture_collapses_duplicate_gameIDs_correctly() {
        // Mirrors the user's real logcat: 4 LEGO Batman deals (gameID=612) across 4 stores,
        // 3 Arkham Knight deals (gameID=107598) across 3 stores, 1 Arkham Origins (gameID=97941).
        val deals = listOf(
            deal(dealID = "lb-21", gameID = 612, salePriceValue = 2.99, dealRating = 8.4),
            deal(dealID = "lb-23", gameID = 612, salePriceValue = 3.00, dealRating = 8.4),
            deal(dealID = "ak-23", gameID = 107598, salePriceValue = 3.45, dealRating = 7.7),
            deal(dealID = "lb-30", gameID = 612, salePriceValue = 2.59, dealRating = 8.3),
            deal(dealID = "ao-23", gameID = 97941, salePriceValue = 3.45, dealRating = 7.9),
            deal(dealID = "ak-21", gameID = 107598, salePriceValue = 3.99, dealRating = 7.5),
            deal(dealID = "lb-15", gameID = 612, salePriceValue = 2.59, dealRating = 7.0),
            deal(dealID = "ak-7", gameID = 107598, salePriceValue = 3.99, dealRating = 7.2),
        )

        val grouped = deals.groupByGame()

        // 8 rows → 3 groups, ordered by first occurrence (LEGO Batman, Arkham Knight, Arkham Origins).
        assertEquals(listOf(612, 107598, 97941), grouped.map { it.gameID })
        assertEquals(listOf(4, 3, 1), grouped.map { it.totalDealCount })
        // Cheapest LEGO Batman is $2.59 — there are two at that price, tie broken by dealRating 8.3 > 7.0.
        assertEquals("lb-30", grouped[0].cheapestDeal.dealID)
        // Cheapest Arkham Knight is $3.45 (single).
        assertEquals("ak-23", grouped[1].cheapestDeal.dealID)
    }
}
