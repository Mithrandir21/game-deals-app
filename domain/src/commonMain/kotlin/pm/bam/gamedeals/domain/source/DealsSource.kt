package pm.bam.gamedeals.domain.source

import pm.bam.gamedeals.domain.models.Bundle
import pm.bam.gamedeals.domain.models.Country
import pm.bam.gamedeals.domain.models.Deal
import pm.bam.gamedeals.domain.models.DealDetails
import pm.bam.gamedeals.domain.models.DealsQuery
import pm.bam.gamedeals.domain.models.Game
import pm.bam.gamedeals.domain.models.GameDetails
import pm.bam.gamedeals.domain.models.GameMeta
import pm.bam.gamedeals.domain.models.PriceHistory
import pm.bam.gamedeals.domain.models.RegionalPrice
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
     * Lightweight game identity (title + boxart) by its source game id, over ITAD `/games/info/v2`.
     * Unlike [fetchGameDetails] this skips the price/deal fetch — it's used to enrich id-only lists such
     * as the user's notes ("My notes", #283). The returned [Game]'s price fields are unset.
     */
    suspend fun fetchGame(gameId: String): Game

    /**
     * The historical low-price time series for a game (by its source game id), for the price-history
     * chart (#208). Providers that cannot supply a series return an empty [PriceHistory].
     *
     * [since] is an optional epoch-millisecond lower bound used by the repository's incremental cache
     * (ITAD caching strategy, Phase 4): when non-null, the provider returns only the points at/after that
     * instant so a stale series can be topped-up rather than refetched. A null [since] returns the full
     * series (cold cache). Providers that can't filter by time may ignore it and return the full series.
     */
    suspend fun fetchPriceHistory(gameId: String, since: Long? = null): PriceHistory

    /**
     * Active storefront bundles for the current region (#205 Phase 3c). Providers without bundles
     * return an empty list.
     */
    suspend fun fetchBundles(): List<Bundle>

    /**
     * Catalogue + live-signal metadata for a game (by its source game id), over ITAD `/games/info/v2`
     * (epic #291, Phase 1) — developers/publishers, tags, release date, storefront/critic reviews,
     * waitlist/collection stats, and current player counts. Powers the redesigned Game Page's Stats tab
     * and header. Providers without this data return a [GameMeta] with empty collections / null signals.
     */
    suspend fun fetchGameMeta(gameId: String): GameMeta

    /**
     * The bundles that contain the given game (by its source game id), over ITAD `/games/bundles/v2`
     * (epic #291, Phase 1) — drives the Game Page's "Found in bundles" section. Providers without this
     * data return an empty list.
     */
    suspend fun fetchBundlesForGame(gameId: String): List<Bundle>

    /**
     * The game's cheapest current price in each of [countries] (epic #291, Phase 7 — the Game Page's
     * "Regions" tab), one `/games/prices/v3` query per region. Regions with no current deal are omitted.
     * Prices are in each region's own currency and are not converted/comparable as raw values.
     */
    suspend fun fetchRegionalPrices(gameId: String, countries: List<Country>): List<RegionalPrice>

    suspend fun fetchStores(): List<Store>
}
