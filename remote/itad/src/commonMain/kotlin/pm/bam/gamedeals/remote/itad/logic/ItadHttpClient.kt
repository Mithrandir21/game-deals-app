package pm.bam.gamedeals.remote.itad.logic

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import pm.bam.gamedeals.remote.logic.RemoteBuildType
import pm.bam.gamedeals.remote.logic.RemoteBuildUtil
import pm.bam.gamedeals.remote.logic.httpClient
import pm.bam.gamedeals.remote.logic.ktorPlatformLogger

/**
 * IsThereAnyDeal-specific HttpClient. Mirrors the shared `gameDealsHttpClient` config (timeouts,
 * JSON content negotiation, debug-only logging) but installs a `defaultRequest` block that sets the
 * ITAD base URL plus the static `ITAD-API-Key` header that authenticates every call.
 *
 * ITAD accepts the key either as the `key` query parameter or the `ITAD-API-Key` header; we use the
 * header so the key never appears in logged/cached URLs.
 *
 * The [engine] parameter exists so tests can drive this client config against a `MockEngine`.
 */
internal fun itadHttpClient(
    json: Json,
    buildUtil: RemoteBuildUtil,
    apiKey: String,
    engine: HttpClientEngine? = null,
): HttpClient {
    val config: io.ktor.client.HttpClientConfig<*>.() -> Unit = {
        expectSuccess = true

        install(ContentNegotiation) { json(json) }

        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 30_000
        }

        // ITAD rate-limits without published quotas and can revoke access without notice, so a 429
        // must be weathered rather than surfaced as an error. Retry transparently on 429 only —
        // safe for any HTTP method since the request was throttled, not processed — honoring the
        // server's `Retry-After` header and otherwise backing off exponentially.
        install(HttpRequestRetry) {
            maxRetries = ITAD_MAX_RETRIES
            retryIf { _, response -> response.status == HttpStatusCode.TooManyRequests }
            exponentialDelay(respectRetryAfterHeader = true)
        }

        when (buildUtil.buildType()) {
            RemoteBuildType.DEBUG -> install(Logging) {
                logger = ktorPlatformLogger
                level = LogLevel.HEADERS
            }
            RemoteBuildType.RELEASE -> Unit
        }

        defaultRequest {
            url(ITAD_BASE_URL)
            header("ITAD-API-Key", apiKey)
        }
    }

    return if (engine != null) HttpClient(engine, config) else httpClient(config)
}

internal const val ITAD_HOST = "api.isthereanydeal.com"
internal const val ITAD_BASE_URL = "https://api.isthereanydeal.com"

/** Retry attempts after the initial request when ITAD returns 429 (see [itadHttpClient]). */
private const val ITAD_MAX_RETRIES = 3
