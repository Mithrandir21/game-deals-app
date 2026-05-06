package pm.bam.gamedeals.feature.home.ui

import androidx.compose.runtime.Composable
import pm.bam.gamedeals.domain.models.Store

@Composable
fun HomeRoute(
    onSearch: () -> Unit,
    goToGame: (gameId: Int) -> Unit,
    onViewStoreDeals: (store: Store) -> Unit = {},
    onViewGiveaways: () -> Unit,
    goToWeb: (url: String, gameTitle: String) -> Unit,
) {
    HomeScreen(
        onSearch = onSearch,
        goToGame = goToGame,
        onViewStoreDeals = onViewStoreDeals,
        onViewGiveaways = onViewGiveaways,
        goToWeb = goToWeb,
    )
}
