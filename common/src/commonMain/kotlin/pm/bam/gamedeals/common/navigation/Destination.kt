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

    @Serializable
    data object Home : Destination

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

    /** The deals inside one daily notification (#272 follow-up). Owns its Scaffold/TopAppBar + back arrow. */
    @Serializable
    data class NotificationDetail(val notificationId: String) : Destination

    @Serializable
    data object IgnoredGames : Destination

    @Serializable
    data object MyNotes : Destination

    @Serializable
    data object LinkedAccounts : Destination

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

    /** Giveaway detail (in-app), reached from the Giveaways list. Owns its Scaffold/TopAppBar + back arrow. */
    @Serializable
    data class GiveawayDetail(val giveawayId: Int) : Destination

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
