package pm.bam.gamedeals.feature.home.ui

import androidx.compose.runtime.Composable
import pm.bam.gamedeals.domain.models.Store

/**
 * Public entry point into the home feature. Wraps the internal [HomeScreen] so
 * platform consumers (Android nav graph, iOS root composable) can render it
 * without exposing the underlying internals.
 */
@Composable
fun HomeRoute(
    onSearch: () -> Unit,
    goToGame: (gameId: String) -> Unit,
    onViewStoreDeals: (store: Store) -> Unit = {},
    onViewGiveaways: () -> Unit,
    onViewFavourites: () -> Unit,
    goToWeb: (url: String, gameTitle: String) -> Unit,
    goToGameDetails: (steamAppId: Int, title: String) -> Unit,
    goToGameDetailsByTitle: (title: String) -> Unit,
) {
    HomeScreen(
        onSearch = onSearch,
        goToGame = goToGame,
        onViewStoreDeals = onViewStoreDeals,
        onViewGiveaways = onViewGiveaways,
        onViewFavourites = onViewFavourites,
        goToWeb = goToWeb,
        goToGameDetails = goToGameDetails,
        goToGameDetailsByTitle = goToGameDetailsByTitle,
    )
}
