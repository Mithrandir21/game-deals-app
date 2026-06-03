package pm.bam.gamedeals.di

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import org.koin.dsl.module
import pm.bam.gamedeals.integration.support.FixtureRequestHandler.handle
import pm.bam.gamedeals.remote.gamerpower.api.GamesApi as GamerpowerGamesApi
import pm.bam.gamedeals.remote.gamerpower.di.GAMERPOWER_QUALIFIER
import pm.bam.gamedeals.remote.logic.RemoteBuildType
import pm.bam.gamedeals.remote.logic.RemoteBuildUtil
import pm.bam.gamedeals.remote.logic.gameDealsHttpClient

/**
 * Test-only Koin module that replaces the production GamerPower HttpClient binding with a Ktor
 * MockEngine client backed by [FixtureRequestHandler]. Loaded last by
 * [pm.bam.gamedeals.TestGameDealsApplication] so Koin's last-load-wins semantics override the
 * production network module. (CheapShark was removed in Phase 4, epic #205.)
 */
val testNetworkOverridesModule = module {
    // RELEASE-mode build util suppresses the production Logging plugin install,
    // matching the prior hand-rolled HttpClient shape.
    single<RemoteBuildUtil> { RemoteBuildUtil { RemoteBuildType.RELEASE } }

    single<HttpClient>(GAMERPOWER_QUALIFIER) {
        gameDealsHttpClient(
            json = get(),
            buildUtil = get(),
            baseUrl = "https://www.gamerpower.com",
            engine = MockEngine { request -> handle(request) },
        )
    }

    // The Api class binding is produced from the qualified HttpClient above by the production module.
    // It uses `get(qualifier)`, which resolves to whichever binding for that qualifier was registered
    // last — the test one.
    single { GamerpowerGamesApi(get(GAMERPOWER_QUALIFIER)) }
}
