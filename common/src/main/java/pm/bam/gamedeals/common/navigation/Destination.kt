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

    @Serializable
    data class Store(val storeId: Int) : Destination

    @Serializable
    data class Game(val gameId: Int) : Destination

    @Serializable
    data object Search : Destination

    @Serializable
    data class WebView(val url: String, val gameTitle: String) : Destination

    @Serializable
    data object Giveaways : Destination
}
