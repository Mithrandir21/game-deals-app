package pm.bam.gamedeals.remote.itad.auth.oauth

/**
 * Cryptographically secure random bytes for the PKCE code verifier and the CSRF `state` (RFC 7636).
 *
 * Backed by the platform CSPRNG — `java.security.SecureRandom` on Android, `SecRandomCopyBytes`
 * (Security framework) on iOS — rather than [kotlin.random.Random], which is not guaranteed to be a
 * CSPRNG on every target (epic #219, Phase 6.2 / #239).
 */
internal expect fun secureRandomBytes(size: Int): ByteArray
