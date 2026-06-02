package pm.bam.gamedeals.domain.source

import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.DealDetails
import pm.bam.gamedeals.domain.models.Game
import pm.bam.gamedeals.domain.models.GameDetails
import pm.bam.gamedeals.domain.models.SearchParameters
import pm.bam.gamedeals.domain.models.Store

/**
 * Source-neutral facade over the remote deals provider.
 *
 * Encapsulates the HTTP client wiring, transport DTOs, mappers, logging, and
 * exception transformation so that callers (repositories, mediators) only
 * depend on this interface and domain models — never on a specific provider.
 *
 * The current implementation (`CheapsharkSourceImpl`) lives in
 * `:remote:cheapshark`; this interface is the domain-side port and only
 * references domain types, so the provider can be swapped (e.g. to ITAD)
 * without touching callers.
 */
interface DealsSource {

    suspend fun fetchDealsForStore(query: SearchParameters? = null): List<Deal>

    suspend fun fetchDealDetails(id: String): DealDetails

    suspend fun fetchGames(
        title: String,
        steamAppID: Int? = null,
        limit: Int? = null,
        pageNumber: Int? = null
    ): List<Game>

    suspend fun fetchGameDetails(id: String): GameDetails

    suspend fun fetchStores(): List<Store>
}
