package pm.bam.gamedeals.remote.itad

import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.auth.AuthTokenStore
import pm.bam.gamedeals.domain.models.ItadUser
import pm.bam.gamedeals.domain.source.ItadAccountSource
import pm.bam.gamedeals.domain.source.ItadLoginSource
import pm.bam.gamedeals.remote.itad.auth.ItadCredentials
import pm.bam.gamedeals.remote.itad.auth.oauth.AuthBrowserLauncher
import pm.bam.gamedeals.remote.itad.auth.oauth.AuthRedirectResult
import pm.bam.gamedeals.remote.itad.auth.oauth.ItadOAuthClient
import pm.bam.gamedeals.remote.itad.auth.oauth.generatePkce
import pm.bam.gamedeals.remote.itad.auth.oauth.randomState

/**
 * Orchestrates the ITAD OAuth login (epic #219, Phase 2.4): PKCE → browser authorize → code exchange →
 * persist tokens → `/user/info` → re-persist with the username.
 *
 * The tokens are saved **before** the `/user/info` call (with a blank username) so the bearer client can
 * authenticate that call; they're re-saved with the real username immediately after — so `AuthState`
 * may briefly read `LoggedIn("")`. A `state` mismatch or a provider error throws; a user cancel returns
 * null.
 */
internal class ItadLoginSourceImpl(
    private val oauthClient: ItadOAuthClient,
    private val browserLauncher: AuthBrowserLauncher,
    private val accountSource: ItadAccountSource,
    private val authTokenStore: AuthTokenStore,
    private val credentials: ItadCredentials,
    private val clock: Clock,
) : ItadLoginSource {

    override suspend fun login(): ItadUser? {
        val pkce = generatePkce()
        val state = randomState()
        val authorizeUrl = oauthClient.buildAuthorizeUrl(pkce.codeChallenge, state)
        val scheme = credentials.redirectUri.substringBefore("://")

        return when (val result = browserLauncher.authorize(authorizeUrl, scheme)) {
            is AuthRedirectResult.Cancelled -> null
            is AuthRedirectResult.Failed -> throw IllegalStateException("ITAD login failed: ${result.reason}")
            is AuthRedirectResult.Success -> {
                check(result.state == state) { "ITAD login failed: OAuth state mismatch" }

                val token = oauthClient.exchangeCodeForToken(result.code, pkce.codeVerifier)
                val expiresAt = clock.nowMillis() + token.expiresIn * 1000L
                val refresh = token.refreshToken.orEmpty()

                // Provisional save so the bearer client can authenticate the /user/info call…
                authTokenStore.saveTokens(token.accessToken, refresh, expiresAt, username = "")
                val user = accountSource.getUserInfo()
                // …then persist with the real username.
                authTokenStore.saveTokens(token.accessToken, refresh, expiresAt, username = user.username)
                user
            }
        }
    }
}
