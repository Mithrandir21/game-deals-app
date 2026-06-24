package pm.bam.gamedeals

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module
import pm.bam.gamedeals.common.di.commonAndroidModule
import pm.bam.gamedeals.common.di.commonModule
import pm.bam.gamedeals.common.ui.di.commonUiModule
import pm.bam.gamedeals.di.appModule
import pm.bam.gamedeals.di.testDatabaseOverridesModule
import pm.bam.gamedeals.di.testImageLoaderOverridesModule
import pm.bam.gamedeals.di.testNetworkOverridesModule
import pm.bam.gamedeals.domain.di.domainAndroidModule
import pm.bam.gamedeals.domain.di.domainModule
import pm.bam.gamedeals.feature.game.di.gameModule
import pm.bam.gamedeals.feature.giveaways.di.giveawaysModule
import pm.bam.gamedeals.feature.home.di.homeModule
import pm.bam.gamedeals.feature.store.di.storeModule
import pm.bam.gamedeals.logging.di.loggingAndroidModule
import pm.bam.gamedeals.remote.di.remoteModule
import pm.bam.gamedeals.remote.gamerpower.di.gamerpowerNetworkModule
import pm.bam.gamedeals.remote.gamerpower.di.gamerpowerRemoteModule
import pm.bam.gamedeals.remote.itad.auth.ItadCredentials
import pm.bam.gamedeals.remote.itad.di.itadNetworkModule
import pm.bam.gamedeals.remote.itad.di.itadRemoteModule
import pm.bam.gamedeals.remote.logic.RemoteBuildType

/**
 * Application class swapped in by [KoinTestRunner] for instrumented tests. Loads the
 * production Koin modules and then layers `test*OverridesModule` on top; Koin's
 * last-load-wins semantics let those overrides redefine the CheapShark/GamerPower
 * HttpClient + DomainDatabase bindings without touching production code.
 */
class TestGameDealsApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@TestGameDealsApplication)
            modules(
                loggingAndroidModule,
                commonModule,
                commonAndroidModule,
                commonUiModule,
                domainModule,
                domainAndroidModule,
                remoteModule(RemoteBuildType.RELEASE),
                // ITAD is the live DealsSource; the GamerPower client is overridden with a
                // MockEngine by testNetworkOverridesModule. ITAD isn't exercised by any current
                // instrumented test, so it is left unmocked (real binding, never resolved).
                itadNetworkModule,
                itadRemoteModule,
                module { single { ItadCredentials(BuildConfig.ITAD_API_KEY) } },
                gamerpowerNetworkModule,
                gamerpowerRemoteModule,
                appModule,
                homeModule,
                gameModule,
                giveawaysModule,
                storeModule,
                testNetworkOverridesModule,
                testDatabaseOverridesModule,
                testImageLoaderOverridesModule,
            )
        }
    }
}
