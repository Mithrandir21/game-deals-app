package pm.bam.gamedeals.remote.cheapshark

import pm.bam.gamedeals.remote.cheapshark.api.models.deals.RemoteDealsQuery
import pm.bam.gamedeals.remote.cheapshark.models.RemoteDeal
import pm.bam.gamedeals.remote.cheapshark.models.RemoteDealDetails
import pm.bam.gamedeals.remote.cheapshark.models.RemoteGame
import pm.bam.gamedeals.remote.cheapshark.models.RemoteGameDetails
import pm.bam.gamedeals.remote.cheapshark.models.RemoteRelease
import pm.bam.gamedeals.remote.cheapshark.models.RemoteStore

/**
 * Single deep facade over the CheapShark remote API.
 *
 * Encapsulates Retrofit `*Api` wiring, logging, and exception transformation
 * so that callers (repositories, mediators) only depend on this interface.
 */
interface CheapsharkSource {

    suspend fun fetchDealsForStore(query: RemoteDealsQuery? = null): List<RemoteDeal>

    suspend fun fetchDealDetails(id: String): RemoteDealDetails

    suspend fun fetchGames(
        title: String,
        steamAppID: Int? = null,
        limit: Int? = null,
        pageNumber: Int? = null
    ): List<RemoteGame>

    suspend fun fetchGameDetails(id: String): RemoteGameDetails

    suspend fun fetchReleases(): List<RemoteRelease>

    suspend fun fetchStores(): List<RemoteStore>
}
