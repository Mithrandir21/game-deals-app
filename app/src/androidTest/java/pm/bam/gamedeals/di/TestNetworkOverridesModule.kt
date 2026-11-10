package pm.bam.gamedeals.di

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import org.koin.dsl.module
import pm.bam.gamedeals.integration.support.FixtureRequestHandler.handle
import pm.bam.gamedeals.remote.gamerpower.api.GamesApi as GamerpowerGamesApi
import pm.bam.gamedeals.remote.gamerpower.di.GAMERPOWER_QUALIFIER
import pm.bam.gamedeals.remote.igdb.di.IGDB_QUALIFIER
import pm.bam.gamedeals.remote.igdb.di.IGDB_TOKEN_QUALIFIER
import pm.bam.gamedeals.remote.itad.di.ITAD_AUTH_QUALIFIER
import pm.bam.gamedeals.remote.itad.di.ITAD_OAUTH_QUALIFIER
import pm.bam.gamedeals.remote.itad.di.ITAD_QUALIFIER
import pm.bam.gamedeals.remote.logic.RemoteBuildType
import pm.bam.gamedeals.remote.logic.RemoteBuildUtil
import pm.bam.gamedeals.remote.logic.gameDealsHttpClient

/**
 * Test-only Koin module that replaces the production network clients: GamerPower with a Ktor
 * MockEngine client backed by [FixtureRequestHandler], ITAD and IGDB with clients that answer 404. Loaded last by
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

    // ITAD and IGDB answer every call with 404, so screens show their error states instead of live data.
    // A test that needs their data replaces the repository or source it reads from.
    listOf(ITAD_QUALIFIER, ITAD_OAUTH_QUALIFIER, ITAD_AUTH_QUALIFIER, IGDB_QUALIFIER, IGDB_TOKEN_QUALIFIER).forEach { qualifier ->
        single<HttpClient>(qualifier) {
            gameDealsHttpClient(
                json = get(),
                buildUtil = get(),
                baseUrl = "https://offline.invalid",
                engine = MockEngine { respondError(HttpStatusCode.NotFound) },
            )
        }
    }
}
