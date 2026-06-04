package pm.bam.gamedeals.domain.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.Serializable
import pm.bam.gamedeals.common.storage.Storage
import pm.bam.gamedeals.domain.models.AuthState

/**
 * Persists the ITAD OAuth token set (epic #219, Phase 2) via [Storage] and exposes the derived
 * [AuthState] reactively — mirroring `RegionRepository`'s lazily-seeded `StateFlow` pattern so the
 * UI re-renders the moment the user logs in or out.
 *
 * SECURITY: [Storage] is plain `SharedPreferences` / `NSUserDefaults` — NOT encrypted. Moving tokens
 * to `EncryptedSharedPreferences` / Keychain is tracked as Phase 6.2 (#239).
 */
interface AuthTokenStore {
    fun observeAuthState(): Flow<AuthState>
    suspend fun getAccessToken(): String?
    suspend fun getRefreshToken(): String?

    /** Epoch-millisecond access-token expiry, or `0L` if nothing is stored. */
    suspend fun getExpiresAtEpochMs(): Long

    suspend fun saveTokens(accessToken: String, refreshToken: String, expiresAtEpochMs: Long, username: String)
    suspend fun clear()
}

internal const val AUTH_TOKEN_KEY = "itad_auth_token"

@Serializable
internal data class StoredAuthToken(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochMs: Long,
    val username: String,
)

internal class AuthTokenStoreImpl(
    private val storage: Storage,
) : AuthTokenStore {

    // Reactive source of truth, lazily seeded from [storage] on first access (null = not yet loaded).
    private val authState = MutableStateFlow<AuthState?>(null)

    override fun observeAuthState(): Flow<AuthState> =
        authState
            .onStart { if (authState.value == null) authState.value = loadFromStorage().toAuthState() }
            .filterNotNull()

    override suspend fun getAccessToken(): String? = loadFromStorage()?.accessToken

    override suspend fun getRefreshToken(): String? = loadFromStorage()?.refreshToken

    override suspend fun getExpiresAtEpochMs(): Long = loadFromStorage()?.expiresAtEpochMs ?: 0L

    override suspend fun saveTokens(accessToken: String, refreshToken: String, expiresAtEpochMs: Long, username: String) {
        val token = StoredAuthToken(accessToken, refreshToken, expiresAtEpochMs, username)
        storage.save(AUTH_TOKEN_KEY, token, StoredAuthToken.serializer())
        authState.value = token.toAuthState()
    }

    override suspend fun clear() {
        storage.remove(AUTH_TOKEN_KEY)
        authState.value = AuthState.LoggedOut
    }

    private suspend fun loadFromStorage(): StoredAuthToken? =
        runCatching { storage.getNullable(AUTH_TOKEN_KEY, StoredAuthToken.serializer()) }.getOrNull()

    private fun StoredAuthToken?.toAuthState(): AuthState =
        this?.let { AuthState.LoggedIn(it.username) } ?: AuthState.LoggedOut
}
