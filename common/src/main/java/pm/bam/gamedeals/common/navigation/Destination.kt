package pm.bam.gamedeals.common.navigation

import kotlinx.serialization.Serializable

/**
 * Type-safe navigation destinations for the app's Compose [androidx.navigation.NavHost].
 *
 * Each destination is `@Serializable` and is used directly with `composable<...>`,
 * `navController.navigate(...)`, and `SavedStateHandle.toRoute<...>()` so that the
 * navigation graph fails to compile on any typo in an argument key or type.
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
