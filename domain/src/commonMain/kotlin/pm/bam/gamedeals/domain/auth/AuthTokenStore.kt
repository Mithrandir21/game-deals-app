package pm.bam.gamedeals.domain.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * SECURITY: this is wired to the SECURE_QUALIFIER [Storage] — encrypted at rest via the Android Keystore
 * (AES/GCM) and the iOS Keychain (#239), not the plain settings store. The token shape and key string are
 * unchanged; only the backing store differs.
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

    /**
     * Fills in the username on the already-stored token, leaving the token material untouched.
     *
     * Exists for the case where login succeeded but the `/user/info` fetch that follows it did not, which
     * persists a blank username the user is otherwise stuck with until they sign out and back in. Scoped
     * to just this field rather than going through [saveTokens] so a concurrent token refresh can't be
     * clobbered by a stale access token read moments earlier. No-op when logged out or when [username]
     * is blank.
     *
     * The backfill runs at app scope on login, concurrently with the ITAD 401-refresh path and with a
     * logout the user can trigger at any moment, so implementations must serialise it against the other
     * mutations rather than relying on the narrow field scope alone.
     */
    suspend fun updateUsername(username: String)

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

    /**
     * Serialises every mutation. [updateUsername] is a read-modify-write, and the three writers run
     * concurrently by design — the username backfill is kicked off at app scope on login, the ITAD
     * token refresh writes from whichever request hit a 401, and the user can sign out at any point.
     * Unsynchronised, a backfill that loaded before a logout would re-save the old token afterwards and
     * silently resurrect a dead session; the same interleaving against a refresh would roll the tokens
     * back to the pre-refresh pair.
     */
    private val mutex = Mutex()

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
        mutex.withLock {
            storage.save(AUTH_TOKEN_KEY, token)
            authState.value = token.toAuthState()
        }
    }

    override suspend fun updateUsername(username: String) {
        if (username.isBlank()) return
        mutex.withLock {
            val current = loadFromStorage() ?: return
            val updated = current.copy(username = username)
            storage.save(AUTH_TOKEN_KEY, updated)
            authState.value = updated.toAuthState()
        }
    }

    override suspend fun clear() {
        mutex.withLock {
            storage.remove(AUTH_TOKEN_KEY)
            authState.value = AuthState.LoggedOut
        }
    }

    private suspend fun loadFromStorage(): StoredAuthToken? =
        runCatching { storage.getNullable<StoredAuthToken>(AUTH_TOKEN_KEY) }.getOrNull()

    private fun StoredAuthToken?.toAuthState(): AuthState =
        this?.let { AuthState.LoggedIn(it.username, needsReconnect = it.scopeVersion < CURRENT_SCOPE_VERSION) }
            ?: AuthState.LoggedOut
}
