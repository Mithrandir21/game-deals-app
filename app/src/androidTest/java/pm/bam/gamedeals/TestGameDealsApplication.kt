package pm.bam.gamedeals

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.dsl.module
import pm.bam.gamedeals.common.di.commonAndroidModule
import pm.bam.gamedeals.common.di.commonModule
import pm.bam.gamedeals.common.version.AppInfo
import pm.bam.gamedeals.common.ui.di.commonUiModule
import pm.bam.gamedeals.di.appModule
import pm.bam.gamedeals.di.testDatabaseOverridesModule
import pm.bam.gamedeals.di.testImageLoaderOverridesModule
import pm.bam.gamedeals.di.testNetworkOverridesModule
import pm.bam.gamedeals.domain.di.domainAndroidModule
import pm.bam.gamedeals.domain.di.domainModule
import pm.bam.gamedeals.feature.account.di.accountModule
import pm.bam.gamedeals.feature.appupdate.di.appUpdateModule
import pm.bam.gamedeals.feature.bundles.di.bundlesModule
import pm.bam.gamedeals.feature.deals.di.dealsModule
import pm.bam.gamedeals.feature.discover.di.discoverModule
import pm.bam.gamedeals.feature.game.di.gameModule
import pm.bam.gamedeals.feature.giveaways.di.giveawaysModule
import pm.bam.gamedeals.feature.home.di.homeModule
import pm.bam.gamedeals.feature.onboarding.di.onboardingModule
import pm.bam.gamedeals.feature.store.di.storeModule
import pm.bam.gamedeals.logging.analytics.AnalyticsConfig
import pm.bam.gamedeals.logging.di.loggingAndroidModule
import pm.bam.gamedeals.remote.di.remoteModule
import pm.bam.gamedeals.remote.gamerpower.di.gamerpowerNetworkModule
import pm.bam.gamedeals.remote.gamerpower.di.gamerpowerRemoteModule
import pm.bam.gamedeals.remote.igdb.auth.IgdbCredentials
import pm.bam.gamedeals.remote.igdb.di.igdbNetworkModule
import pm.bam.gamedeals.remote.igdb.di.igdbRemoteModule
import pm.bam.gamedeals.remote.itad.auth.ItadCredentials
import pm.bam.gamedeals.remote.itad.di.itadAndroidModule
import pm.bam.gamedeals.remote.itad.di.itadNetworkModule
import pm.bam.gamedeals.remote.itad.di.itadRemoteModule
import pm.bam.gamedeals.remote.logic.RemoteBuildType

/**
 * Application class swapped in by [KoinTestRunner] for instrumented tests. Loads the
 * production Koin modules and then layers `test*OverridesModule` on top; Koin's
 * last-load-wins semantics let those overrides redefine the network clients, the
 * DomainDatabase and the image loader without touching production code. Keep the module
 * list in step with GameDealsApplication, or MainActivity can't open every screen.
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
                // Every network client (ITAD, IGDB, GamerPower) is replaced by testNetworkOverridesModule,
                // so no test reaches a real server.
                itadNetworkModule,
                itadRemoteModule,
                itadAndroidModule,
                module { single { ItadCredentials(apiKey = "test-key") } },
                igdbNetworkModule,
                igdbRemoteModule,
                module { single { IgdbCredentials(clientId = "test-client", clientSecret = "test-secret") } },
                gamerpowerNetworkModule,
                gamerpowerRemoteModule,
                module {
                    single { AnalyticsConfig(environment = "test", appVersion = BuildConfig.VERSION_NAME) }
                    single { AppInfo(versionName = BuildConfig.VERSION_NAME, versionCode = BuildConfig.VERSION_CODE.toLong(), storeId = packageName, isDebug = true) }
                },
                // The same feature modules as the production app, so MainActivity can open any screen.
                appModule,
                homeModule,
                gameModule,
                giveawaysModule,
                storeModule,
                bundlesModule,
                accountModule,
                dealsModule,
                discoverModule,
                onboardingModule,
                appUpdateModule,
                testNetworkOverridesModule,
                testDatabaseOverridesModule,
                testImageLoaderOverridesModule,
            )
        }
    }
}
