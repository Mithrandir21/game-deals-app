package pm.bam.gamedeals.di

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import org.koin.dsl.module
import pm.bam.gamedeals.integration.support.FixtureRequestHandler.handle
import pm.bam.gamedeals.remote.cheapshark.api.DealsApi
import pm.bam.gamedeals.remote.cheapshark.api.GamesApi as CheapsharkGamesApi
import pm.bam.gamedeals.remote.cheapshark.api.ReleaseApi
import pm.bam.gamedeals.remote.cheapshark.api.StoresApi
import pm.bam.gamedeals.remote.cheapshark.di.CHEAPSHARK_QUALIFIER
import pm.bam.gamedeals.remote.gamerpower.api.GamesApi as GamerpowerGamesApi
import pm.bam.gamedeals.remote.gamerpower.di.GAMERPOWER_QUALIFIER
import pm.bam.gamedeals.remote.logic.RemoteBuildType
import pm.bam.gamedeals.remote.logic.RemoteBuildUtil
import pm.bam.gamedeals.remote.logic.gameDealsHttpClient

/**
 * Test-only Koin module that replaces the production CheapShark + GamerPower HttpClient
 * bindings with Ktor MockEngine clients backed by [FixtureRequestHandler]. Loaded last by
 * [pm.bam.gamedeals.TestGameDealsApplication] so Koin's last-load-wins semantics override
 * the production network modules.
 */
val testNetworkOverridesModule = module {
    // RELEASE-mode build util suppresses the production Logging plugin install,
    // matching the prior hand-rolled HttpClient shape.
    val testBuildUtil = RemoteBuildUtil { RemoteBuildType.RELEASE }

    single<HttpClient>(CHEAPSHARK_QUALIFIER) {
        gameDealsHttpClient(
            json = get(),
            buildUtil = testBuildUtil,
            baseUrl = "https://www.cheapshark.com",
            engine = MockEngine { request -> handle(request) },
        )
    }

    single<HttpClient>(GAMERPOWER_QUALIFIER) {
        gameDealsHttpClient(
            json = get(),
            buildUtil = testBuildUtil,
            baseUrl = "https://www.gamerpower.com",
            engine = MockEngine { request -> handle(request) },
        )
    }

    // The Api class bindings are produced from the qualified HttpClient above by
    // production modules. They use `get(qualifier)` which resolves to whichever
    // binding for that qualifier was registered last — the test ones.
    single { DealsApi(get(CHEAPSHARK_QUALIFIER)) }
    single { CheapsharkGamesApi(get(CHEAPSHARK_QUALIFIER)) }
    single { StoresApi(get(CHEAPSHARK_QUALIFIER)) }
    single { ReleaseApi(get(CHEAPSHARK_QUALIFIER)) }
    single { GamerpowerGamesApi(get(GAMERPOWER_QUALIFIER)) }
}
