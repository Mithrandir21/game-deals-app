package pm.bam.gamedeals.remote.cheapshark.di

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.url
import io.ktor.serialization.kotlinx.json.json
import org.koin.dsl.module
import pm.bam.gamedeals.remote.cheapshark.api.DealsApi
import pm.bam.gamedeals.remote.cheapshark.api.GamesApi
import pm.bam.gamedeals.remote.cheapshark.api.ReleaseApi
import pm.bam.gamedeals.remote.cheapshark.api.StoresApi
import pm.bam.gamedeals.remote.logic.KtorLogcatLogger
import pm.bam.gamedeals.remote.logic.RemoteBuildType
import pm.bam.gamedeals.remote.logic.httpClient

val cheapsharkNetworkModule = module {
    single<HttpClient>(CHEAPSHARK_QUALIFIER) {
        httpClient {
            expectSuccess = true

            install(ContentNegotiation) {
                json(get())
            }

            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 30_000
            }

            when (get<pm.bam.gamedeals.remote.logic.RemoteBuildUtil>().buildType()) {
                RemoteBuildType.DEBUG -> install(Logging) {
                    logger = KtorLogcatLogger
                    level = LogLevel.HEADERS
                }
                RemoteBuildType.RELEASE -> Unit
            }

            defaultRequest { url("https://www.cheapshark.com") }
        }
    }

    single { DealsApi(get(CHEAPSHARK_QUALIFIER)) }
    single { GamesApi(get(CHEAPSHARK_QUALIFIER)) }
    single { StoresApi(get(CHEAPSHARK_QUALIFIER)) }
    single { ReleaseApi(get(CHEAPSHARK_QUALIFIER)) }
}
