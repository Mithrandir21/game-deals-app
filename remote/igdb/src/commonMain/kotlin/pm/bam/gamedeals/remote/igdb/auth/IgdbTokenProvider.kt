package pm.bam.gamedeals.remote.igdb.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.request.forms.submitForm
import io.ktor.http.parameters
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Fetches and caches the Twitch `client_credentials` bearer token used to authorise IGDB calls.
 *
 * Phase 1 keeps the token in-memory only; Twitch issues ~60-day tokens so a single fetch per
 * process is the common case. The Auth plugin invokes [cachedTokens] for `loadTokens` and
 * [fetchToken] for `refreshTokens` — concurrent refresh requests are serialised by the plugin,
 * but the [mutex] guards reads/writes of [cached] against the (rare) cross-pipeline race.
 */
class IgdbTokenProvider(
    private val tokenClient: HttpClient,
    private val credentials: IgdbCredentials,
) {
    private val mutex = Mutex()
    private var cached: BearerTokens? = null

    suspend fun cachedTokens(): BearerTokens? = mutex.withLock { cached }

    suspend fun fetchToken(): BearerTokens {
        val response: RemoteTwitchTokenResponse = tokenClient.submitForm(
            url = "/oauth2/token",
            formParameters = parameters {
                append("client_id", credentials.clientId)
                append("client_secret", credentials.clientSecret)
                append("grant_type", "client_credentials")
            },
        ).body()
        val tokens = BearerTokens(accessToken = response.accessToken, refreshToken = response.accessToken)
        mutex.withLock { cached = tokens }
        return tokens
    }
}
