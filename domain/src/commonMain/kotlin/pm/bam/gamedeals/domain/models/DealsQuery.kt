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
    val sortField: DealsSortField = DealsSortField.Hottest,
    val sortDirection: DealsSortDirection = DealsSortField.Hottest.defaultDirection,
    val shopIds: List<Int> = emptyList(),
    val mature: Boolean = false,
    val filter: DealsFilter = DealsFilter(),
    val offset: Int = 0,
    val limit: Int = DEALS_PAGE_SIZE,
) {
    /**
     * The ITAD `sort` token sent to `/deals/v2` — the field's [DealsSortField.token] with a leading
     * `-` for [DealsSortDirection.Descending] (ascending is the bare token). E.g. `Hottest` +
     * `Descending` → `-hot`; `Price` + `Ascending` → `price`.
     */
    val sortApiValue: String
        get() = (if (sortDirection == DealsSortDirection.Descending) "-" else "") + sortField.token

    companion object {
        /** Page size for offset-based load-more on the Deals tab. */
        const val DEALS_PAGE_SIZE: Int = 30
    }
}

/** Sort direction for a [DealsSortField]; maps to the optional `-` prefix on the ITAD `sort` token. */
enum class DealsSortDirection { Ascending, Descending }

/**
 * Sort fields offered by ITAD's "Current Deals" page, recovered from the website's deals bundle and
 * verified live against `POST /deals/v2` (every field accepts both a bare/ascending and a
 * `-`-prefixed/descending form). [token] is the bare API token; [defaultDirection] is the direction
 * the website applies when the field is first selected.
 */
enum class DealsSortField(val token: String, val defaultDirection: DealsSortDirection) {
    /** Hottest deals first (`hot`) — the website's default ordering. */
    Hottest("hot", DealsSortDirection.Descending),

    /** Most recently added first (`time`). */
    Newest("time", DealsSortDirection.Descending),

    /** Biggest discount first (`cut`). */
    PriceCut("cut", DealsSortDirection.Descending),

    /** Cheapest first (`price`, ascending). */
    Price("price", DealsSortDirection.Ascending),

    /** Soonest to expire first (`expiry`, ascending). */
    Expiry("expiry", DealsSortDirection.Ascending),

    /** Most recently released first (`release-date`). */
    ReleaseDate("release-date", DealsSortDirection.Descending),

    /** Most popular first (`rank`, ascending — rank 1 is most popular). */
    Popular("rank", DealsSortDirection.Ascending),

    /** Most-waitlisted first (`waitlisted`). */
    Waitlisted("waitlisted", DealsSortDirection.Descending),

    /** Most-collected first (`collected`). */
    Collected("collected", DealsSortDirection.Descending),

    /** Most current Steam players first (`steam-players`). */
    SteamPlayers("steam-players", DealsSortDirection.Descending),

    /** Highest Steam review percentage first (`steam-reviews`). */
    SteamReviews("steam-reviews", DealsSortDirection.Descending),

    /** Most Steam reviews first (`steam-reviews-count`). */
    SteamReviewsCount("steam-reviews-count", DealsSortDirection.Descending),

    /** Highest OpenCritic score first (`opencritic`). */
    OpenCritic("opencritic", DealsSortDirection.Descending),

    /** Highest Metacritic score first (`metacritic`). */
    Metacritic("metacritic", DealsSortDirection.Descending),

    /** Highest Metacritic user score first (`metacritic-user`). */
    MetacriticUser("metacritic-user", DealsSortDirection.Descending),
}
