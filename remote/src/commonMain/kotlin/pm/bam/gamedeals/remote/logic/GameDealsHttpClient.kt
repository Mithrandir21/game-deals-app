package pm.bam.gamedeals.remote.logic

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.url
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Shared HttpClient factory for the CheapShark and GamerPower remote modules.
 * Wires JSON content negotiation, request/connect timeouts, debug-only Ktor
 * logging, and the per-source base URL.
 *
 * If [engine] is null, the platform-default engine ([httpClient]) is used.
 * Tests can pass a `MockEngine` to drive the same client config against
 * recorded request handlers.
 */
fun gameDealsHttpClient(
    json: Json,
    buildUtil: RemoteBuildUtil,
    baseUrl: String,
    engine: HttpClientEngine? = null,
): HttpClient {
    val config: HttpClientConfig<*>.() -> Unit = {
        expectSuccess = true

        install(ContentNegotiation) {
            json(json)
        }

        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 30_000
        }

        when (buildUtil.buildType()) {
            RemoteBuildType.DEBUG -> install(Logging) {
                logger = ktorPlatformLogger
                level = LogLevel.HEADERS
            }
            RemoteBuildType.RELEASE -> Unit
        }

        defaultRequest { url(baseUrl) }
    }

    return if (engine != null) HttpClient(engine, config) else httpClient(config)
}
