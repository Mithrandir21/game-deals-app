package pm.bam.gamedeals.remote.itad.logic

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.url
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import pm.bam.gamedeals.remote.itad.auth.oauth.ItadTokenProvider
import pm.bam.gamedeals.remote.logic.RemoteBuildType
import pm.bam.gamedeals.remote.logic.RemoteBuildUtil
import pm.bam.gamedeals.remote.logic.httpClient
import pm.bam.gamedeals.remote.logic.ktorPlatformLogger

/**
 * Plain JSON client (no `ITAD-API-Key` header) for the OAuth token endpoint (epic #219, Phase 2). The
 * OAuth host differs from the API host, so calls use absolute URLs rather than a base URL.
 *
 * [engine] lets tests drive the config against a `MockEngine`.
 */
internal fun itadOAuthHttpClient(
    json: Json,
    buildUtil: RemoteBuildUtil,
    engine: HttpClientEngine? = null,
): HttpClient {
    val config: HttpClientConfig<*>.() -> Unit = {
        expectSuccess = true
        install(ContentNegotiation) { json(json) }
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
    }
    return if (engine != null) HttpClient(engine, config) else httpClient(config)
}

/**
 * Bearer-authenticated client for the ITAD user endpoints (waitlist/collection/profile, Phase 2). Sends
 * the user's OAuth access token (supplied by [tokenProvider]) to the ITAD API host and transparently
 * refreshes it on a 401. Distinct from the API-key client so the bearer never leaks onto key-only calls.
 */
internal fun itadAuthHttpClient(
    json: Json,
    buildUtil: RemoteBuildUtil,
    tokenProvider: ItadTokenProvider,
    engine: HttpClientEngine? = null,
): HttpClient {
    val config: HttpClientConfig<*>.() -> Unit = {
        expectSuccess = true
        install(ContentNegotiation) { json(json) }
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
        install(Auth) {
            bearer {
                loadTokens { tokenProvider.currentBearerTokens() }
                refreshTokens { tokenProvider.refresh() }
                sendWithoutRequest { true }
            }
        }
        defaultRequest {
            url(ITAD_BASE_URL)
        }
    }
    return if (engine != null) HttpClient(engine, config) else httpClient(config)
}
