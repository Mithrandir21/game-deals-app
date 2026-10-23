package pm.bam.gamedeals.remote.itad.auth.oauth

import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlin.coroutines.cancellation.CancellationException
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.auth.AuthTokenStore
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.error
import pm.bam.gamedeals.logging.warn

/**
 * Bridges the persisted [AuthTokenStore] and the [ItadOAuthClient] for Ktor's `Auth { bearer }` plugin
 * (epic #219, Phase 2): supplies the current access token and transparently refreshes on 401.
 *
 * On refresh success the new tokens are persisted (preserving the stored username). The session is cleared
 * only when ITAD rejects the refresh token; a network, server or rate-limit failure keeps it for the next attempt.
 */
class ItadTokenProvider(
    private val authTokenStore: AuthTokenStore,
    private val oauthClient: ItadOAuthClient,
    private val clock: Clock,
    private val logger: Logger,
) {

    /** Current tokens for the bearer plugin's `loadTokens`, or null when logged out. */
    suspend fun currentBearerTokens(): BearerTokens? {
        val access = authTokenStore.getAccessToken() ?: return null
        return BearerTokens(access, authTokenStore.getRefreshToken().orEmpty())
    }

    /** Refresh handler for the bearer plugin's `refreshTokens` (called on 401). */
    suspend fun refresh(): BearerTokens? {
        val refresh = authTokenStore.getRefreshToken() ?: return clearAndNull()
        return try {
            val token = oauthClient.refreshToken(refresh)
            val newRefresh = token.refreshToken ?: refresh
            authTokenStore.saveTokens(
                accessToken = token.accessToken,
                refreshToken = newRefresh,
                expiresAtEpochMs = clock.nowMillis() + token.expiresIn * 1000L,
                username = authTokenStore.getUsername().orEmpty(),
                // A refresh grants no new scopes — preserve the stored scope version so it doesn't
                // accidentally clear (or set) the needsReconnect flag.
                scopeVersion = authTokenStore.getScopeVersion(),
            )
            BearerTokens(token.accessToken, newRefresh)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: ClientRequestException) {
            if (e.response.status in DEFINITIVE_REJECTIONS) {
                if (e.isRevokedGrant()) {
                    // The user's own grant ended (revoked on ITAD, or expired): an ordinary sign-out, not a defect.
                    warn(logger, throwable = e) { "ITAD refresh token revoked or expired; clearing session" }
                } else {
                    error(logger, throwable = e) { "ITAD rejected the refresh token; clearing session" }
                }
                clearAndNull()
            } else {
                keepSession(e)
            }
        } catch (t: Throwable) {
            keepSession(t)
        }
    }

    /**
     * OAuth's `invalid_grant` is specific to this user's refresh token. Other rejections (`invalid_client`,
     * `invalid_request`) point at the app's own credentials or request and would hit every user, so they stay errors.
     */
    private suspend fun ClientRequestException.isRevokedGrant(): Boolean =
        try {
            "invalid_grant" in response.bodyAsText()
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Throwable) {
            false
        }

    private fun keepSession(t: Throwable): BearerTokens? {
        error(logger, throwable = t) { "ITAD token refresh failed; keeping the session for the next attempt" }
        return null
    }

    private suspend fun clearAndNull(): BearerTokens? {
        authTokenStore.clear()
        return null
    }

    private companion object {
        // OAuth answers a revoked or expired refresh token with 400 (invalid_grant) or 401 (invalid_client).
        val DEFINITIVE_REJECTIONS = setOf(HttpStatusCode.BadRequest, HttpStatusCode.Unauthorized)
    }
}
