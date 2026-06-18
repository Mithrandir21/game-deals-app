package pm.bam.gamedeals.domain.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.serialization.Serializable
import pm.bam.gamedeals.common.storage.Storage
import pm.bam.gamedeals.common.storage.getNullable
import pm.bam.gamedeals.common.storage.save
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

    /** The signed-in username, or null if logged out. Preserved across token refreshes. */
    suspend fun getUsername(): String?

    /** Epoch-millisecond access-token expiry, or `0L` if nothing is stored. */
    suspend fun getExpiresAtEpochMs(): Long

    /**
     * The OAuth scope version the persisted token was granted under (see [CURRENT_SCOPE_VERSION]), or
     * `0` if nothing is stored. A token-refresh must preserve this (refreshing grants no new scopes).
     */
    suspend fun getScopeVersion(): Int

    suspend fun saveTokens(
        accessToken: String,
        refreshToken: String,
        expiresAtEpochMs: Long,
        username: String,
        scopeVersion: Int,
    )

    suspend fun clear()
}

internal const val AUTH_TOKEN_KEY = "itad_auth_token"

/**
 * Bumped whenever the requested OAuth scope set changes (see
 * [pm.bam.gamedeals.remote.itad.auth.oauth.ItadOAuthConfig.SCOPES]). A persisted token stamped with a
 * lower [StoredAuthToken.scopeVersion] drives `AuthState.LoggedIn(needsReconnect = true)` so the UI can
 * prompt a re-authentication to grant the newer scopes (#273).
 *
 * History: v1 added notifications/ignored/notes/profiles on top of the original #219 scope set (v0).
 */
const val CURRENT_SCOPE_VERSION = 1

@Serializable
internal data class StoredAuthToken(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochMs: Long,
    val username: String,
    // Defaults to 0 so a token persisted before this field existed (the original scope set) reads
    // back as the legacy version → needsReconnect, exactly as intended for already-signed-in users.
    val scopeVersion: Int = 0,
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

    override suspend fun getUsername(): String? = loadFromStorage()?.username

    override suspend fun getExpiresAtEpochMs(): Long = loadFromStorage()?.expiresAtEpochMs ?: 0L

    override suspend fun getScopeVersion(): Int = loadFromStorage()?.scopeVersion ?: 0

    override suspend fun saveTokens(
        accessToken: String,
        refreshToken: String,
        expiresAtEpochMs: Long,
        username: String,
        scopeVersion: Int,
    ) {
        val token = StoredAuthToken(accessToken, refreshToken, expiresAtEpochMs, username, scopeVersion)
        storage.save(AUTH_TOKEN_KEY, token)
        authState.value = token.toAuthState()
    }

    override suspend fun clear() {
        storage.remove(AUTH_TOKEN_KEY)
        authState.value = AuthState.LoggedOut
    }

    private suspend fun loadFromStorage(): StoredAuthToken? =
        runCatching { storage.getNullable<StoredAuthToken>(AUTH_TOKEN_KEY) }.getOrNull()

    private fun StoredAuthToken?.toAuthState(): AuthState =
        this?.let { AuthState.LoggedIn(it.username, needsReconnect = it.scopeVersion < CURRENT_SCOPE_VERSION) }
            ?: AuthState.LoggedOut
}
