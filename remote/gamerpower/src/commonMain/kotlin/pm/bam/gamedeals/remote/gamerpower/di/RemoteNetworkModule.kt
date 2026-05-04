package pm.bam.gamedeals.remote.gamerpower.di

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.url
import io.ktor.serialization.kotlinx.json.json
import org.koin.dsl.module
import pm.bam.gamedeals.remote.gamerpower.api.GamesApi
import pm.bam.gamedeals.remote.logic.RemoteBuildType
import pm.bam.gamedeals.remote.logic.RemoteBuildUtil
import pm.bam.gamedeals.remote.logic.httpClient
import pm.bam.gamedeals.remote.logic.ktorPlatformLogger

val gamerpowerNetworkModule = module {
    single<HttpClient>(GAMERPOWER_QUALIFIER) {
        httpClient {
            expectSuccess = true

            install(ContentNegotiation) {
                json(get())
            }

            install(HttpTimeout) {
                connectTimeoutMillis = 10_000
                requestTimeoutMillis = 30_000
            }

            when (get<RemoteBuildUtil>().buildType()) {
                RemoteBuildType.DEBUG -> install(Logging) {
                    logger = ktorPlatformLogger
                    level = LogLevel.HEADERS
                }
                RemoteBuildType.RELEASE -> Unit
            }

            defaultRequest { url("https://www.gamerpower.com") }
        }
    }

    single { GamesApi(get(GAMERPOWER_QUALIFIER)) }
}
