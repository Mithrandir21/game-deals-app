package pm.bam.gamedeals.remote.itad.auth

/** The default OAuth redirect URI for the ITAD account flow (epic #219, Phase 2). */
const val DEFAULT_ITAD_REDIRECT_URI: String = "pm.bam.gamedeals://oauth/itad"

/**
 * IsThereAnyDeal API credentials.
 *
 * [apiKey] (from `BuildConfig.ITAD_API_KEY` / iOS `ITADApiKey`) is sent as the `ITAD-API-Key` header
 * on every request by [pm.bam.gamedeals.remote.itad.logic.itadHttpClient] and authenticates the
 * application-scoped endpoints (deals, games, bundles, stats).
 *
 * [oauthClientId] (from `BuildConfig.ITAD_OAUTH_CLIENT_ID` / iOS `ITADOAuthClientId`) and [redirectUri]
 * are used by the user-scoped OAuth flow (epic #219, Phase 2 — account, waitlist, collection). They are
 * wired here in Phase 0 but not yet consumed at runtime; the OAuth client lands in Phase 2 (#226).
 */
data class ItadCredentials(
    val apiKey: String,
    val oauthClientId: String = "",
    val redirectUri: String = DEFAULT_ITAD_REDIRECT_URI,
)
