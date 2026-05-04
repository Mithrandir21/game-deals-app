package pm.bam.gamedeals.iosApp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.collections.immutable.persistentListOf
import org.koin.core.context.startKoin
import org.koin.dsl.module
import org.koin.mp.KoinPlatform
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import pm.bam.gamedeals.common.di.commonModule
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.di.domainIosModule
import pm.bam.gamedeals.domain.di.domainModule
import pm.bam.gamedeals.domain.models.Store
import pm.bam.gamedeals.domain.repositories.stores.StoresRepository
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
        // iOS counterpart of the Android `appModule` Clock binding. Coil
        // ImageLoader is deferred until iOS image loading actually matters
        // (Coil 3 has an iOS engine but tying it to Ktor here is Phase 7
        // polish work).
        single<Clock> { Clock { (NSDate().timeIntervalSince1970 * 1000.0).toLong() } }
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
            iosAppModule,
        )
    }
}

@Composable
private fun App() {
    val storesRepository = remember { KoinPlatform.getKoin().get<StoresRepository>() }
    val stores: List<Store> by storesRepository.observeStores()
        .collectAsState(initial = persistentListOf())

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Hello from Game Deals KMP")
            Text("Stores fetched: ${stores.size}")
            stores.take(5).forEach { store ->
                Text("• ${store.storeName}")
            }
        }
    }
}
