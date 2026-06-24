package pm.bam.gamedeals.remote.logic

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.url
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.Json

/**
 * Shared HttpClient factory for the CheapShark and GamerPower remote modules.
 * Wires JSON content negotiation, request/connect timeouts, debug-only Ktor
 * logging, and the per-source base URL.
 *
 * [maxConcurrency] (when non-null) installs a [Semaphore]-backed concurrency cap, and
 * [retryOnTooManyRequests] installs a 429-only exponential-backoff retry — both opt-in so the bare
 * factory (e.g. the IGDB token client) is unaffected, while community feeds like GamerPower can match
 * the resilience the ITAD/IGDB clients configure inline.
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
    maxConcurrency: Int? = null,
    retryOnTooManyRequests: Boolean = false,
    maxRetries: Int = DEFAULT_MAX_RETRIES,
): HttpClient {
    val config: HttpClientConfig<*>.() -> Unit = {
        expectSuccess = true

        install(ContentNegotiation) {
            json(json)
        }

        // Opt-in concurrency cap: bounds simultaneous in-flight calls so a fan-out burst can't spike the
        // upstream. A throughput cap, not a per-interval rate limit. Mirrors the ITAD/IGDB limiters.
        if (maxConcurrency != null) {
            install(GameDealsConcurrencyLimiter) { permits = maxConcurrency }
        }

        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 30_000
        }

        // Opt-in 429 retry: a throttled request was not processed, so retrying any method is safe.
        // Honors the server's Retry-After header, otherwise backs off exponentially.
        if (retryOnTooManyRequests) {
            install(HttpRequestRetry) {
                this.maxRetries = maxRetries
                retryIf { _, response -> response.status == HttpStatusCode.TooManyRequests }
                exponentialDelay(respectRetryAfterHeader = true)
            }
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

/** Default retry attempts after the initial request when an upstream returns 429. */
const val DEFAULT_MAX_RETRIES = 3

/** Config for [GameDealsConcurrencyLimiter]. */
class GameDealsConcurrencyLimiterConfig {
    /** Maximum requests allowed in flight at once. */
    var permits: Int = 1
}

/**
 * Shared Ktor client plugin that bounds simultaneous in-flight requests through a [Semaphore]: a request
 * acquires a permit before being sent (via the [Send] hook) and releases it once the call completes, so
 * callers past the limit suspend until a permit frees. The reusable counterpart to the ITAD/IGDB
 * module-local limiters — a throughput (concurrency) cap, not a per-interval rate limit.
 */
val GameDealsConcurrencyLimiter = createClientPlugin("GameDealsConcurrencyLimiter", ::GameDealsConcurrencyLimiterConfig) {
    val semaphore = Semaphore(permits = pluginConfig.permits)
    on(Send) { request ->
        semaphore.withPermit { proceed(request) }
    }
}
