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
    data class Search(val initialQuery: String? = null) : Destination

    @Serializable
    data class WebView(val url: String, val gameTitle: String) : Destination

    @Serializable
    data object Giveaways : Destination

    @Serializable
    data object Settings : Destination

    @Serializable
    data object Bundles : Destination

    @Serializable
    data class BundleDetail(val bundleId: Int) : Destination
}
