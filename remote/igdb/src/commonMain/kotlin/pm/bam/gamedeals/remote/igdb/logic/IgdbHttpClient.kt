package pm.bam.gamedeals.remote.igdb.logic

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.Send
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.bearer
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
import pm.bam.gamedeals.remote.igdb.auth.IgdbCredentials
import pm.bam.gamedeals.remote.igdb.auth.IgdbTokenProvider
import pm.bam.gamedeals.remote.logic.RemoteBuildType
import pm.bam.gamedeals.remote.logic.RemoteBuildUtil
import pm.bam.gamedeals.remote.logic.httpClient
import pm.bam.gamedeals.remote.logic.ktorPlatformLogger

/**
 * IGDB-specific HttpClient. Mirrors the shared `gameDealsHttpClient` config (timeouts, JSON
 * content negotiation, debug-only logging) but installs the Ktor `Auth` bearer plugin and a
 * `defaultRequest` block that sets both the base URL and the static `Client-ID` header — IGDB
 * requires both on every call. The Auth plugin adds `Authorization: Bearer <token>` once a
 * token is loaded or refreshed via [IgdbTokenProvider].
 *
 * `sendWithoutRequest` restricts bearer attachment to `api.igdb.com` so the plugin can't leak
 * the token if this client is ever reused for another host.
 *
 * The [engine] parameter exists so tests can share a single `MockEngine` between this client
 * and the token client to verify the full refresh round-trip.
 */
internal fun igdbHttpClient(
    json: Json,
    buildUtil: RemoteBuildUtil,
    credentials: IgdbCredentials,
    tokenProvider: IgdbTokenProvider,
    engine: HttpClientEngine? = null,
): HttpClient {
    val config: io.ktor.client.HttpClientConfig<*>.() -> Unit = {
        expectSuccess = true

        install(ContentNegotiation) { json(json) }

        // IGDB allows 4 req/s and up to 8 open connections. A Semaphore bounds simultaneous in-flight
        // calls so the tag-discovery vocabulary fan-out and any future paging bursts can't spike past
        // the connection cap. Mirrors the ITAD concurrency limiter.
        install(IgdbConcurrencyLimiter) { permits = IGDB_MAX_CONCURRENCY }

        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 30_000
        }

        // IGDB returns 429 when the 4 req/s budget is exceeded. A throttled request wasn't processed, so
        // retrying is safe — weather it transparently (honoring Retry-After) rather than surfacing an
        // error. Complements the proactive concurrency limiter above; mirrors the ITAD retry.
        install(HttpRequestRetry) {
            maxRetries = IGDB_MAX_RETRIES
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

        install(Auth) {
            bearer {
                loadTokens { tokenProvider.cachedTokens() }
                refreshTokens { tokenProvider.fetchToken() }
                sendWithoutRequest { request -> request.url.host == IGDB_HOST }
            }
        }

        defaultRequest {
            url(IGDB_BASE_URL)
            header("Client-ID", credentials.clientId)
        }
    }

    return if (engine != null) HttpClient(engine, config) else httpClient(config)
}

internal const val IGDB_HOST = "api.igdb.com"
internal const val IGDB_BASE_URL = "https://api.igdb.com"
internal const val TWITCH_TOKEN_BASE_URL = "https://id.twitch.tv"

/** Default cap on simultaneous in-flight IGDB requests (IGDB allows up to 8 open connections). */
private const val IGDB_MAX_CONCURRENCY = 8

/** Retry attempts after the initial request when IGDB returns 429 (see [igdbHttpClient]). */
private const val IGDB_MAX_RETRIES = 3

/** Config for [IgdbConcurrencyLimiter]. */
internal class IgdbConcurrencyLimiterConfig {
    /** Maximum IGDB requests allowed in flight at once. */
    var permits: Int = IGDB_MAX_CONCURRENCY
}

/**
 * Ktor client plugin that bounds simultaneous in-flight IGDB requests through a [Semaphore]
 * (epic #307). A request acquires a permit before being sent (via the [Send] hook) and releases it
 * once the call completes, so callers past the limit suspend until a permit frees. Mirrors the ITAD
 * `ItadConcurrencyLimiter` — a throughput (concurrency) cap, not a per-interval rate limit.
 */
internal val IgdbConcurrencyLimiter = createClientPlugin("IgdbConcurrencyLimiter", ::IgdbConcurrencyLimiterConfig) {
    val semaphore = Semaphore(permits = pluginConfig.permits)
    on(Send) { request ->
        semaphore.withPermit { proceed(request) }
    }
}
