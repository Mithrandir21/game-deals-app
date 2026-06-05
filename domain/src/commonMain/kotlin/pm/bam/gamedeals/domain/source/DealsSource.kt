package pm.bam.gamedeals.domain.source

import pm.bam.gamedeals.domain.models.Bundle
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.DealDetails
import pm.bam.gamedeals.domain.models.DealsQuery
import pm.bam.gamedeals.domain.models.Game
import pm.bam.gamedeals.domain.models.GameDetails
import pm.bam.gamedeals.domain.models.PriceHistory
import pm.bam.gamedeals.domain.models.SearchParameters
import pm.bam.gamedeals.domain.models.Store

/**
 * Source-neutral facade over the remote deals provider.
 *
 * Encapsulates the HTTP client wiring, transport DTOs, mappers, logging, and
 * exception transformation so that callers (repositories, mediators) only
 * depend on this interface and domain models — never on a specific provider.
 *
 * The live implementation (`ItadSourceImpl`) lives in `:remote:itad` (epic #205); this interface is the
 * domain-side port and only references domain types, so the provider can be swapped without touching
 * callers. (The original CheapShark provider was removed in Phase 4.)
 */
interface DealsSource {

    suspend fun fetchDealsForStore(query: SearchParameters? = null): List<Deal>

    /**
     * A general, sorted/filtered page of deals across all stores for the Deals tab (#219 Phase 4),
     * over ITAD `/deals/v2`. Unlike [fetchDealsForStore] this is not store-scoped: it supports a sort
     * order, an optional shop filter and offset paging via [DealsQuery]. The region (`country`) is
     * applied by the implementation from the user's selected region.
     */
    suspend fun fetchDeals(query: DealsQuery): List<Deal>

    suspend fun fetchDealDetails(id: String): DealDetails

    suspend fun fetchGames(
        title: String,
        steamAppID: Int? = null,
        limit: Int? = null,
        pageNumber: Int? = null
    ): List<Game>

    suspend fun fetchGameDetails(id: String): GameDetails

    /**
     * The historical low-price time series for a game (by its source game id), for the price-history
     * chart (#208). Providers that cannot supply a series return an empty [PriceHistory].
     */
    suspend fun fetchPriceHistory(gameId: String): PriceHistory

    /**
     * Active storefront bundles for the current region (#205 Phase 3c). Providers without bundles
     * return an empty list.
     */
    suspend fun fetchBundles(): List<Bundle>

    suspend fun fetchStores(): List<Store>
}
