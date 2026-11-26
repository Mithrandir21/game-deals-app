package pm.bam.gamedeals.remote.igdb.auth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.request.forms.submitForm
import io.ktor.http.parameters
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pm.bam.gamedeals.common.time.Clock

/**
 * Fetches and caches the Twitch `client_credentials` bearer token used to authorise IGDB calls.
 *
 * The token is held in-memory *and* persisted through [store], so a cold start with a token still in
 * date skips the grant entirely — the first IGDB request of the session already carries a bearer,
 * rather than spending a 401 round-trip plus a Twitch call to discover what we knew last launch. That
 * also means a launch with no connectivity can still serve IGDB from a previously-issued token
 * instead of blanking the whole surface (Sentry KOTLIN-7).
 *
 * The Auth plugin invokes [cachedTokens] for `loadTokens` and [fetchToken] for `refreshTokens`.
 * Because the plugin caches `loadTokens` itself, [cachedTokens] is effectively a once-per-client
 * cold-start read; a token that lapses mid-session is caught by the 401 → [fetchToken] path instead.
 * Concurrent refreshes are serialised by the plugin, but [mutex] guards [cached] against the (rare)
 * cross-pipeline race.
 */
internal class IgdbTokenProvider(
    private val tokenClient: HttpClient,
    private val credentials: IgdbCredentials,
    private val store: IgdbTokenStore,
    private val clock: Clock,
) {
    private val mutex = Mutex()
    private var cached: StoredIgdbToken? = null

    suspend fun cachedTokens(): BearerTokens? = mutex.withLock {
        val token = cached ?: store.load()?.also { cached = it }
        token?.takeIf { clock.nowMillis() < it.expiresAtEpochMs }?.toBearerTokens()
    }

    suspend fun fetchToken(): BearerTokens {
        val response: RemoteTwitchTokenResponse = tokenClient.submitForm(
            url = "/oauth2/token",
            formParameters = parameters {
                append("client_id", credentials.clientId)
                append("client_secret", credentials.clientSecret)
                append("grant_type", "client_credentials")
            },
        ).body()

        val token = StoredIgdbToken(
            accessToken = response.accessToken,
            expiresAtEpochMs = clock.nowMillis() + (response.expiresIn * MILLIS_PER_SECOND) - EXPIRY_SKEW_MILLIS,
        )
        mutex.withLock { cached = token }
        store.save(token)
        return token.toBearerTokens()
    }

    // `client_credentials` issues no refresh token — the only way to renew is to run the grant again,
    // which is exactly what the Auth plugin calls `refreshTokens` (→ [fetchToken]) to do.
    private fun StoredIgdbToken.toBearerTokens() = BearerTokens(accessToken = accessToken, refreshToken = null)

    internal companion object {

        /**
         * Retire a token slightly early so an IGDB call can't be sent with a bearer that lapses in
         * flight. Purely an optimisation — an expired token still self-heals via 401 → [fetchToken].
         */
        const val EXPIRY_SKEW_MILLIS: Long = 5L * 60L * 1000L

        private const val MILLIS_PER_SECOND: Long = 1000L
    }
}
