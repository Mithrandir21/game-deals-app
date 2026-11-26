package pm.bam.gamedeals.remote.itad.auth.oauth

/**
 * Opens the OAuth authorize URL in a platform secure browser and suspends until the provider redirects
 * back to the app's custom scheme (epic #219, Phase 2.2). Implemented per-platform:
 * - Android: a browser `Intent` + a redirect `Activity` (in `:app`) that delivers the callback via
 *   `AuthRedirectBus`.
 * - iOS: `ASWebAuthenticationSession` with `callbackURLScheme`.
 *
 * Bound via the per-platform Koin modules (`itadAndroidModule` / `itadIosModule`). Consumed by the
 * login orchestration in Phase 2.4 (#229).
 */
interface AuthBrowserLauncher {
    /**
     * @param authorizeUrl the full `/oauth/authorize` URL (from `ItadOAuthClient.buildAuthorizeUrl`).
     * @param redirectScheme the redirect URI's scheme only (e.g. `pm.bam.gamedeals`).
     */
    suspend fun authorize(authorizeUrl: String, redirectScheme: String): AuthRedirectResult
}

/** Outcome of the browser authorize round-trip. */
sealed interface AuthRedirectResult {
    /** The provider redirected back with an authorization [code] (and the echoed CSRF [state]). */
    data class Success(val code: String, val state: String?) : AuthRedirectResult

    /** The user dismissed the browser without authorizing. */
    data object Cancelled : AuthRedirectResult

    /** The provider returned an error, or the redirect could not be parsed. */
    data class Failed(val reason: String) : AuthRedirectResult
}
