package pm.bam.gamedeals.iosApp

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.window.ComposeUIViewController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.dsl.module
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIViewController
import pm.bam.gamedeals.common.di.commonModule
import pm.bam.gamedeals.common.navigation.Destination
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.common.ui.theme.darkScheme
import pm.bam.gamedeals.common.ui.theme.lightScheme
import pm.bam.gamedeals.domain.di.domainIosModule
import pm.bam.gamedeals.domain.di.domainModule
import pm.bam.gamedeals.feature.game.di.gameModule
import pm.bam.gamedeals.feature.game.navigation.gameScreen
import pm.bam.gamedeals.feature.giveaways.di.giveawaysModule
import pm.bam.gamedeals.feature.giveaways.navigation.giveawaysScreen
import pm.bam.gamedeals.feature.home.di.homeModule
import pm.bam.gamedeals.feature.home.navigation.homeScreen
import pm.bam.gamedeals.feature.search.di.searchModule
import pm.bam.gamedeals.feature.search.navigation.searchScreen
import pm.bam.gamedeals.feature.store.di.storeModule
import pm.bam.gamedeals.feature.store.navigation.storeScreen
import pm.bam.gamedeals.feature.webview.navigation.webViewScreen
import pm.bam.gamedeals.logging.di.loggingIosModule
import pm.bam.gamedeals.remote.cheapshark.di.cheapsharkNetworkModule
import pm.bam.gamedeals.remote.cheapshark.di.cheapsharkRemoteModule
import pm.bam.gamedeals.remote.di.remoteModule
import pm.bam.gamedeals.remote.gamerpower.di.gamerpowerNetworkModule
import pm.bam.gamedeals.remote.gamerpower.di.gamerpowerRemoteModule

@Suppress("FunctionName", "unused")
fun MainViewController(): UIViewController {
    bootstrapKoin()
    return ComposeUIViewController { App() }
}

private fun bootstrapKoin() {
    if (GlobalContext.getKoinApplicationOrNull() != null) return
    val iosAppModule = module {
        single<Clock> { Clock { (NSDate().timeIntervalSince1970 * 1000.0).toLong() } }
        single<ImageLoader> {
            ImageLoader.Builder(PlatformContext.INSTANCE)
                .crossfade(true)
                .components { add(KtorNetworkFetcherFactory()) }
                .build()
        }
    }

    startKoin {
        modules(
            loggingIosModule,
            commonModule,
            domainModule,
            domainIosModule,
            remoteModule,
            cheapsharkNetworkModule,
            cheapsharkRemoteModule,
            gamerpowerNetworkModule,
            gamerpowerRemoteModule,
            homeModule,
            searchModule,
            gameModule,
            giveawaysModule,
            storeModule,
            iosAppModule,
        )
    }

    SingletonImageLoader.setSafe { _ ->
        org.koin.mp.KoinPlatform.getKoin().get()
    }
}

@Composable
private fun App() {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkScheme else lightScheme,
    ) {
        AppNavHost()
    }
}

@Composable
private fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = Destination.Home,
    ) {
        homeScreen(
            goToSearch = { navController.navigate(Destination.Search) },
            goToGame = { gameId -> navController.navigate(Destination.Game(gameId)) },
            goToStore = { storeId -> navController.navigate(Destination.Store(storeId)) },
            goToGiveaway = { navController.navigate(Destination.Giveaways) },
            goToWeb = { url, gameTitle -> navController.navigate(Destination.WebView(url, gameTitle)) },
        )
        searchScreen(
            goToGame = { gameId -> navController.navigate(Destination.Game(gameId)) },
        )
        gameScreen(
            navController = navController,
            goToWeb = { url, gameTitle -> navController.navigate(Destination.WebView(url, gameTitle)) },
        )
        giveawaysScreen(
            navController = navController,
            goToWeb = { url, gameTitle -> navController.navigate(Destination.WebView(url, gameTitle)) },
        )
        storeScreen(
            navController = navController,
            goToWeb = { url, gameTitle -> navController.navigate(Destination.WebView(url, gameTitle)) },
        )
        webViewScreen(
            onBack = { navController.popBackStack() },
        )
    }
}

