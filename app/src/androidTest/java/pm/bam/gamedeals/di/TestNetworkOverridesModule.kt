package pm.bam.gamedeals.di

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.url
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import pm.bam.gamedeals.integration.support.FixtureRequestHandler.handle
import pm.bam.gamedeals.remote.cheapshark.api.DealsApi
import pm.bam.gamedeals.remote.cheapshark.api.GamesApi as CheapsharkGamesApi
import pm.bam.gamedeals.remote.cheapshark.api.ReleaseApi
import pm.bam.gamedeals.remote.cheapshark.api.StoresApi
import pm.bam.gamedeals.remote.cheapshark.di.CHEAPSHARK_QUALIFIER
import pm.bam.gamedeals.remote.gamerpower.api.GamesApi as GamerpowerGamesApi
import pm.bam.gamedeals.remote.gamerpower.di.GAMERPOWER_QUALIFIER

/**
 * Test-only Koin module that replaces the production CheapShark + GamerPower HttpClient
 * bindings with Ktor MockEngine clients backed by [FixtureRequestHandler]. Loaded last by
 * [pm.bam.gamedeals.TestGameDealsApplication] so Koin's last-load-wins semantics override
 * the production network modules.
 */
val testNetworkOverridesModule = module {
    single<HttpClient>(CHEAPSHARK_QUALIFIER) {
        HttpClient(MockEngine { request -> handle(request) }) {
            expectSuccess = true
            install(ContentNegotiation) { json(get<Json>()) }
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 30_000
            }
            defaultRequest { url("https://www.cheapshark.com") }
        }
    }

    single<HttpClient>(GAMERPOWER_QUALIFIER) {
        HttpClient(MockEngine { request -> handle(request) }) {
            expectSuccess = true
            install(ContentNegotiation) { json(get<Json>()) }
            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 30_000
            }
            defaultRequest { url("https://www.gamerpower.com") }
        }
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
