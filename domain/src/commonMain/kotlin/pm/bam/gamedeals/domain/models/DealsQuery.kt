package pm.bam.gamedeals.domain.models

import androidx.compose.runtime.Immutable

/**
 * Filter & paging parameters for the all-stores Deals tab (epic #219, Phase 4) — a general,
 * sorted/filtered page over ITAD `/deals/v2`.
 *
 * Distinct from [SearchParameters] (which models the CheapShark-era Search screen). The region
 * (`country`) is applied by the source from the user's selected region, so it is not carried here.
 * Callers drive offset-based load-more by re-issuing the query with an advanced [offset].
 */
@Immutable
data class DealsQuery(
    val sort: DealsSort = DealsSort.TopDiscount,
    val shopIds: List<Int> = emptyList(),
    val offset: Int = 0,
    val limit: Int = DEALS_PAGE_SIZE,
) {
    companion object {
        /** Page size for offset-based load-more on the Deals tab. */
        const val DEALS_PAGE_SIZE: Int = 30
    }
}

/** Sort orders supported by ITAD `/deals/v2`, exposed as the Deals tab filter. */
enum class DealsSort(val apiValue: String) {
    /** Biggest discount first (`-cut`). */
    TopDiscount("-cut"),

    /** Most recently added first (`-publish`). */
    RecentlyAdded("-publish"),

    /** Cheapest first (`price`). */
    PriceLowToHigh("price"),
}
