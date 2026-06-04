package pm.bam.gamedeals.remote.itad.auth.oauth

/**
 * ITAD OAuth endpoints + scopes (epic #219, Phase 2). The OAuth endpoints live on the website host
 * (`isthereanydeal.com`), distinct from the API host (`api.isthereanydeal.com`).
 *
 * NOTE: exact paths are taken from the ITAD authentication docs and must be confirmed against the live
 * flow during the Phase 2.4 login smoke test (no live OAuth run is possible on the Linux dev box).
 */
object ItadOAuthConfig {
    const val AUTHORIZE_URL = "https://isthereanydeal.com/oauth/authorize/"
    const val TOKEN_URL = "https://isthereanydeal.com/oauth/token"

    /** Read+write scopes for the account feature (#219): profile, waitlist, collection. */
    const val SCOPES = "user_info wait_read wait_write coll_read coll_write"
}
