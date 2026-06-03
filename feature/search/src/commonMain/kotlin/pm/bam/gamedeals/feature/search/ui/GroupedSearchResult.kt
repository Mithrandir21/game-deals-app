package pm.bam.gamedeals.feature.search.ui

import androidx.compose.runtime.Immutable
import pm.bam.gamedeals.domain.models.Deal

/**
 * One row of search results, aggregating every CheapShark deal that shares a [gameID].
 *
 * CheapShark returns one deal per (game, store) pair, so a single title easily produces ten
 * interleaved rows in the raw response. The list screen renders one [GroupedSearchResult]
 * per game; tapping it opens the existing per-game screen that already lists every store.
 */
@Immutable
internal data class GroupedSearchResult(
    val gameID: String,
    val cheapestDeal: Deal,
    val totalDealCount: Int,
)

/**
 * Collapse a flat list of deals into one row per [Deal.gameID].
 *
 * - **Representative deal:** cheapest [Deal.salePriceValue]; ties broken by highest [Deal.dealRating].
 * - **Group order:** position of each group's first occurrence in the source list — the API
 *   already sorted by DealRating desc, so first-seen is the highest-ranked deal for that game.
 */
internal fun List<Deal>.groupByGame(): List<GroupedSearchResult> {
    if (isEmpty()) return emptyList()

    val groups = linkedMapOf<String, MutableList<Deal>>()
    for (deal in this) {
        groups.getOrPut(deal.gameID) { mutableListOf() }.add(deal)
    }

    return groups.map { (gameID, deals) ->
        val cheapest = deals.minWith(
            compareBy<Deal> { it.salePriceValue }.thenByDescending { it.dealRating ?: 0.0 }
        )
        GroupedSearchResult(gameID = gameID, cheapestDeal = cheapest, totalDealCount = deals.size)
    }
}
