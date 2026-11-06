package pm.bam.gamedeals.common.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation destinations for the app's Compose [androidx.navigation.NavHost].
 *
 * Each destination is `@Serializable` and is used directly with `composable<...>` and
 * `navController.navigate(...)`, so the navigation graph fails to compile on any typo
 * in an argument type. ViewModels read primitive args via
 * `savedStateHandle.get<Int>("propName")` — nav-compose populates the handle with the
 * `@Serializable` property-name keys.
 */
sealed interface Destination {

    /**
     * First-run onboarding carousel (one-time, replayable from the Account hub). Not a top-level tab, so
     * the app shell renders it chrome-free. Used as the start destination on first launch.
     */
    @Serializable
    data object Onboarding : Destination

    @Serializable
    data object Home : Destination

    /** The Winter Sale entry: the sale hub while the sale is on, the guide's own pages before and after. */
    @Serializable
    data object WinterSaleGuide : Destination

    /** The guide's personal list (waitlist, followed series, popular), opened from the hub's "For you" tile. */
    @Serializable
    data object WinterSaleForYou : Destination

    /**
     * One hub shelf in full. [shelf] is a `WinterSaleShelf` key; [saleEndEpochMillis] drives its countdown strip and is the sale's
     * end on this phone's clock (already corrected by the server time), so a phone with a wrong clock still counts down right.
     */
    @Serializable
    data class WinterSaleList(val shelf: String, val saleEndEpochMillis: Long) : Destination

    /** Top-level tab: the full, browsable all-stores deals list (epic #219, Phase 4). */
    @Serializable
    data object Deals : Destination

    /** Top-level tab: ITAD account — login, profile, waitlist, collection (epic #219, Phase 2). */
    @Serializable
    data object Account : Destination

    /** Account hub sub-screens (epic #272, P1.2). Sub-screens own their Scaffold/TopAppBar + back arrow. */
    @Serializable
    data object WaitlistList : Destination

    @Serializable
    data object CollectionList : Destination

    @Serializable
    data object Notifications : Destination

    /**
     * The games + deals notified on one calendar [date] (#7 notification revamp): the day's notification
     * entries aggregated into a single detail. Owns its Scaffold/TopAppBar + back arrow.
     */
    @Serializable
    data class NotificationDay(val date: String) : Destination

    @Serializable
    data object IgnoredGames : Destination

    @Serializable
    data object MyNotes : Destination

    /** The franchises/series the user follows (#7). Owns its Scaffold/TopAppBar + back arrow. */
    @Serializable
    data object FollowedSeriesList : Destination

    @Serializable
    data object LinkedAccounts : Destination

    /**
     * Developer tools, reachable from the Account hub. The entry point renders only in debug builds
     * (`AppInfo.isDebug`), but the route itself is always registered — a nav graph that changes shape between
     * build types is a worse trade than a route nothing links to in release.
     */
    @Serializable
    data object Debug : Destination

    @Serializable
    data class Store(val storeId: Int) : Destination

    @Serializable
    data class Game(val gameId: String) : Destination

    @Serializable
    data class GameDetails(val steamAppId: Int, val title: String? = null) : Destination

    @Serializable
    data class GameDetailsByIgdbId(val igdbGameId: Long) : Destination

    @Serializable
    data class GameDetailsByTitle(val title: String) : Destination

    @Serializable
    data class WebView(val url: String, val gameTitle: String) : Destination

    @Serializable
    data object Giveaways : Destination

    @Serializable
    data object Bundles : Destination

    @Serializable
    data class BundleDetail(val bundleId: Int) : Destination

    /** Tag-discovery picker (epic #307). Owns its Scaffold/TopAppBar + back arrow. */
    @Serializable
    data object Discover : Destination

    /**
     * Tag-discovery results for an AND-combined [pm.bam.gamedeals.domain.models.IgdbTagFilter]
     * (epic #307). The filter's per-dimension id lists are encoded as comma-joined strings (empty =
     * none) so the route stays on nav-compose's natively-supported primitive arg types; the
     * `DiscoverResultsViewModel` decodes them back into a filter. Owns its Scaffold/TopAppBar.
     */
    @Serializable
    data class DiscoverResults(
        val genreIds: String = "",
        val themeIds: String = "",
        val gameModeIds: String = "",
        val perspectiveIds: String = "",
        val keywordIds: String = "",
    ) : Destination
}
