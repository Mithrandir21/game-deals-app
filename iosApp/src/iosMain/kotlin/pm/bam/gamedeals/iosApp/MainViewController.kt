package pm.bam.gamedeals.iosApp

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.ComposeUIViewController
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.ktor3.KtorNetworkFetcherFactory
import coil3.request.crossfade
import org.koin.core.context.startKoin
import org.koin.dsl.module
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import pm.bam.gamedeals.common.di.commonModule
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.di.domainIosModule
import pm.bam.gamedeals.domain.di.domainModule
import pm.bam.gamedeals.feature.home.di.homeModule
import pm.bam.gamedeals.feature.home.ui.HomeRoute
import pm.bam.gamedeals.logging.di.loggingIosModule
import pm.bam.gamedeals.remote.cheapshark.di.cheapsharkNetworkModule
import pm.bam.gamedeals.remote.cheapshark.di.cheapsharkRemoteModule
import pm.bam.gamedeals.remote.di.remoteModule
import pm.bam.gamedeals.remote.gamerpower.di.gamerpowerNetworkModule
import pm.bam.gamedeals.remote.gamerpower.di.gamerpowerRemoteModule
import platform.UIKit.UIViewController

@Suppress("FunctionName", "unused")
fun MainViewController(): UIViewController {
    bootstrapKoin()
    // `enforceStrictPlistSanityCheck = false` skips Compose Multiplatform's
    // requirement for `CADisableMinimumFrameDurationOnPhone` in Info.plist.
    // Phase 7 polish: emit a real Info.plist with the key set so high-refresh-
    // rate iPhones get full performance.
    return ComposeUIViewController(configure = { enforceStrictPlistSanityCheck = false }) { App() }
}

private var koinStarted = false

private fun bootstrapKoin() {
    if (koinStarted) return
    koinStarted = true
    val iosAppModule = module {
        // iOS counterpart of the Android `appModule` Clock binding.
        single<Clock> { Clock { (NSDate().timeIntervalSince1970 * 1000.0).toLong() } }

        // Coil 3 ImageLoader for iOS — uses Ktor (already in graph) for fetching.
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
            iosAppModule,
        )
    }

    // Wire Coil's singleton image loader to the Koin-bound one so AsyncImage
    // can resolve images without per-call configuration.
    SingletonImageLoader.setSafe { context ->
        // Koin lookup is safe here — bootstrapKoin runs before MainViewController()
        // returns, so the graph is ready by the time Compose first composes.
        org.koin.mp.KoinPlatform.getKoin().get()
    }
}

@Composable
private fun App() {
    HomeRoute(
        onSearch = {},
        goToGame = {},
        onViewStoreDeals = {},
        onViewGiveaways = {},
        goToWeb = { _, _ -> },
    )
}
