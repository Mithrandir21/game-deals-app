package pm.bam.gamedeals.remote.itad.auth.oauth

/**
 * ITAD OAuth endpoints + scopes (epic #219, Phase 2). The OAuth endpoints live on the website host
 * (`isthereanydeal.com`), distinct from the API host (`api.isthereanydeal.com`).
 *
 * Both endpoints REQUIRE the trailing slash. ITAD 302-redirects the slash-less paths
 * (`/oauth/token` → `/oauth/token/`); Ktor follows the 302 on a POST as a GET, dropping the form body
 * (client_id, code, code_verifier), so the token endpoint rejects it as `invalid_client`
 * ("Client authentication failed"). Verified live: `POST /oauth/token/` with client_id + PKCE and
 * **no** client_secret returns `invalid_grant` for a bad code — i.e. client auth passes, ITAD is a
 * public PKCE client (matching its docs: "authorization code flow with PKCE extension").
 */
object ItadOAuthConfig {
    const val AUTHORIZE_URL = "https://isthereanydeal.com/oauth/authorize/"
    const val TOKEN_URL = "https://isthereanydeal.com/oauth/token/"

    /**
     * Read+write scopes for the account feature: profile, waitlist, collection (#219) plus the Account
     * hub additions (#272/#273) — notifications, ignored games, notes, and 3rd-party profile link/sync.
     *
     * IMPORTANT: whenever this set changes, bump [pm.bam.gamedeals.domain.auth.CURRENT_SCOPE_VERSION] so
     * already-signed-in users (whose token was granted under the old set) are prompted to reconnect.
     */
    const val SCOPES =
        "user_info wait_read wait_write coll_read coll_write notifications ignored_read ignored_write notes_read notes_write profiles"
}
