package pm.bam.gamedeals.remote.igdb.auth

import kotlinx.serialization.Serializable
import pm.bam.gamedeals.common.storage.Storage
import pm.bam.gamedeals.common.storage.getNullable
import pm.bam.gamedeals.common.storage.save

/**
 * Persistence seam for the Twitch `client_credentials` token behind [IgdbTokenProvider].
 *
 * Kept as an interface rather than a direct [Storage] dependency so the provider's tests can drive
 * the cold-start/expiry paths with an in-memory double instead of a serialization round-trip.
 */
internal interface IgdbTokenStore {

    /** The persisted token, or null when nothing is stored (or the stored blob can't be read back). */
    suspend fun load(): StoredIgdbToken?

    suspend fun save(token: StoredIgdbToken)
}

/**
 * @param expiresAtEpochMs already includes [IgdbTokenProvider.EXPIRY_SKEW_MILLIS], so a token is
 *   treated as spent slightly before Twitch would actually reject it.
 */
@Serializable
internal data class StoredIgdbToken(
    val accessToken: String,
    val expiresAtEpochMs: Long,
)

/**
 * SECURITY: wired to the SECURE_QUALIFIER [Storage] — encrypted at rest via the Android Keystore and
 * the iOS Keychain, matching how the ITAD auth token is held. The grant is derived from a secret that
 * already ships in the binary, so this is defence in depth rather than a new secret being introduced.
 */
internal class StorageIgdbTokenStore(
    private val storage: Storage,
) : IgdbTokenStore {

    // A stored blob written by an older shape (or a store that failed to decrypt) must not take the
    // IGDB surface down — fall back to null and let the provider run a fresh grant.
    override suspend fun load(): StoredIgdbToken? =
        runCatching { storage.getNullable<StoredIgdbToken>(IGDB_TOKEN_KEY) }.getOrNull()

    override suspend fun save(token: StoredIgdbToken) {
        runCatching { storage.save(IGDB_TOKEN_KEY, token) }
    }
}

internal const val IGDB_TOKEN_KEY = "igdb_twitch_token"
