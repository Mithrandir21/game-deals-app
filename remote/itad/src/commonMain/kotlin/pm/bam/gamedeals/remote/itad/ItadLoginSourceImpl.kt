package pm.bam.gamedeals.remote.itad

import kotlinx.coroutines.CancellationException
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.auth.AuthTokenStore
import pm.bam.gamedeals.domain.auth.CURRENT_SCOPE_VERSION
import pm.bam.gamedeals.domain.models.ItadUser
import pm.bam.gamedeals.domain.source.ItadAccountSource
import pm.bam.gamedeals.domain.source.ItadLoginSource
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.warn
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
 * may briefly read `LoggedIn("")`. A `state` mismatch or a failed code exchange throws; a user cancel
 * returns null. A failed `/user/info` does **not** throw: the token exchange already succeeded, so the
 * login stands and settles on a blank username.
 */
internal class ItadLoginSourceImpl(
    private val oauthClient: ItadOAuthClient,
    private val browserLauncher: AuthBrowserLauncher,
    private val accountSource: ItadAccountSource,
    private val authTokenStore: AuthTokenStore,
    private val credentials: ItadCredentials,
    private val clock: Clock,
    private val logger: Logger,
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
                authTokenStore.saveTokens(token.accessToken, refresh, expiresAt, username = "", scopeVersion = CURRENT_SCOPE_VERSION)
                // …then persist with the real username. A fresh login grants the full current scope set,
                // so it's stamped with CURRENT_SCOPE_VERSION (clearing any needsReconnect).
                //
                // The profile fetch is best-effort: the token exchange already succeeded, so the user IS
                // signed in. Letting a failed /user/info throw here reported a failed login while the
                // tokens stayed persisted — signed in, blank username, no feedback (Sentry KOTLIN-J).
                val user = try {
                    accountSource.getUserInfo()
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    warn(logger, t) { "ITAD login succeeded but /user/info failed; continuing with a blank username" }
                    ItadUser(username = "")
                }
                authTokenStore.saveTokens(token.accessToken, refresh, expiresAt, username = user.username, scopeVersion = CURRENT_SCOPE_VERSION)
                user
            }
        }
    }
}
