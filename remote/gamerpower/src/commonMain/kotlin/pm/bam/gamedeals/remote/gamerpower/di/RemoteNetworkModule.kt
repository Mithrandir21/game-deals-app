package pm.bam.gamedeals.remote.gamerpower.di

import io.ktor.client.HttpClient
import org.koin.dsl.module
import pm.bam.gamedeals.remote.gamerpower.api.GamesApi
import pm.bam.gamedeals.remote.logic.gameDealsHttpClient

val gamerpowerNetworkModule = module {
    single<HttpClient>(GAMERPOWER_QUALIFIER) {
        gameDealsHttpClient(
            json = get(),
            buildUtil = get(),
            baseUrl = "https://www.gamerpower.com",
            // GamerPower is a community feed with no published quotas — weather a 429 and bound the
            // (currently single-call) fan-out, matching the ITAD/IGDB resilience posture.
            maxConcurrency = GAMERPOWER_MAX_CONCURRENCY,
            retryOnTooManyRequests = true,
        )
    }

    single { GamesApi(get(GAMERPOWER_QUALIFIER), json = get(), logger = get()) }
}

/** Cap on simultaneous in-flight GamerPower requests. */
private const val GAMERPOWER_MAX_CONCURRENCY = 4
