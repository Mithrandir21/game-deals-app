package pm.bam.gamedeals.remote.itad.logic

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
    maxConcurrency: Int = ITAD_MAX_CONCURRENCY,
    engine: HttpClientEngine? = null,
): HttpClient {
    val config: io.ktor.client.HttpClientConfig<*>.() -> Unit = {
        expectSuccess = true

        install(ContentNegotiation) { json(json) }

        // Cap simultaneous in-flight ITAD calls: a Semaphore bounds concurrency so a fan-out burst —
        // e.g. Home firing one `/games/info` per ranked game across two rankings — is throttled to N at
        // a time instead of spiking into a 429. A proactive complement to the reactive 429 retry below
        // and the RequestCoalescer (which only dedups *identical* calls, not the distinct per-game ids here).
        install(ItadConcurrencyLimiter) { permits = maxConcurrency }

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

/** Default cap on simultaneous in-flight ITAD requests (see [ItadConcurrencyLimiter]). */
private const val ITAD_MAX_CONCURRENCY = 5

/** Config for [ItadConcurrencyLimiter]. */
internal class ItadConcurrencyLimiterConfig {
    /** Maximum ITAD requests allowed in flight at once. */
    var permits: Int = ITAD_MAX_CONCURRENCY
}

/**
 * Ktor client plugin that bounds the number of simultaneous in-flight requests through a [Semaphore]
 * (ITAD caching strategy, Phase 5a, #266). Cross-cutting for every ITAD call: a request acquires a permit
 * before being sent (via the [Send] hook) and releases it once the call completes, so callers past the
 * limit suspend until a permit frees rather than all hitting the network at once. This is a *throughput*
 * cap (concurrency), not a per-interval rate limit.
 */
internal val ItadConcurrencyLimiter = createClientPlugin("ItadConcurrencyLimiter", ::ItadConcurrencyLimiterConfig) {
    val semaphore = Semaphore(permits = pluginConfig.permits)
    on(Send) { request ->
        semaphore.withPermit { proceed(request) }
    }
}
