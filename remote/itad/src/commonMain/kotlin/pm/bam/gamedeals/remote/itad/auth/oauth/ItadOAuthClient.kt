package pm.bam.gamedeals.remote.itad.auth.oauth

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.http.URLBuilder
import io.ktor.http.parameters
import kotlinx.serialization.SerializationException
import pm.bam.gamedeals.remote.itad.auth.ItadCredentials
import kotlin.coroutines.cancellation.CancellationException

/**
 * The ITAD OAuth authorization-code + PKCE client (epic #219, Phase 2): builds the authorize URL the
 * browser opens, and performs the `/oauth/token` code-exchange and refresh.
 *
 * Uses absolute URLs (the OAuth host differs from the API host), so [httpClient] is a plain JSON client
 * with no ITAD-API-Key default header (see `itadOAuthHttpClient`). The browser round-trip that yields
 * the `code` is handled by the platform `AuthBrowserLauncher` (Phase 2.2, #227).
 */
class ItadOAuthClient(
    private val httpClient: HttpClient,
    private val credentials: ItadCredentials,
) {

    /** The `/oauth/authorize` URL to open in the browser for the given PKCE challenge + CSRF [state]. */
    fun buildAuthorizeUrl(codeChallenge: String, state: String): String =
        URLBuilder(ItadOAuthConfig.AUTHORIZE_URL).apply {
            parameters.append("client_id", credentials.oauthClientId)
            parameters.append("response_type", "code")
            parameters.append("redirect_uri", credentials.redirectUri)
            parameters.append("scope", ItadOAuthConfig.SCOPES)
            parameters.append("code_challenge", codeChallenge)
            parameters.append("code_challenge_method", "S256")
            parameters.append("state", state)
        }.buildString()

    /** Exchanges an authorization [code] (+ the original [codeVerifier]) for tokens. */
    suspend fun exchangeCodeForToken(code: String, codeVerifier: String): RemoteItadTokenResponse =
        decodeTokenResponse("code exchange") {
            httpClient.submitForm(
                url = ItadOAuthConfig.TOKEN_URL,
                formParameters = parameters {
                    append("grant_type", "authorization_code")
                    append("code", code)
                    append("redirect_uri", credentials.redirectUri)
                    append("client_id", credentials.oauthClientId)
                    append("code_verifier", codeVerifier)
                },
            ).body()
        }

    /** Exchanges a [refreshToken] for a fresh access token. */
    suspend fun refreshToken(refreshToken: String): RemoteItadTokenResponse =
        decodeTokenResponse("token refresh") {
            httpClient.submitForm(
                url = ItadOAuthConfig.TOKEN_URL,
                formParameters = parameters {
                    append("grant_type", "refresh_token")
                    append("refresh_token", refreshToken)
                    append("client_id", credentials.oauthClientId)
                },
            ).body()
        }

    // A token body that can't be parsed into a usable token (e.g. no `access_token`) is a real auth
    // failure — surface it as a typed [ItadOAuthException] instead of leaking a SerializationException.
    private suspend fun decodeTokenResponse(
        operation: String,
        request: suspend () -> RemoteItadTokenResponse,
    ): RemoteItadTokenResponse = try {
        request()
    } catch (ce: CancellationException) {
        throw ce
    } catch (e: SerializationException) {
        throw ItadOAuthException("ITAD $operation returned a malformed token response", e)
    }
}
