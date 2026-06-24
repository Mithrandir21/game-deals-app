package pm.bam.gamedeals.remote.itad.auth.oauth

/**
 * A typed OAuth failure surfaced to callers instead of a raw kotlinx `SerializationException`.
 *
 * A `/oauth/token` response without a usable `access_token` is a *genuine* auth failure (not a
 * recoverable display gap), so the token DTOs keep their fields required — but the resulting parse
 * failure is reported as this typed error rather than leaking a parser exception to the UI.
 */
class ItadOAuthException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
