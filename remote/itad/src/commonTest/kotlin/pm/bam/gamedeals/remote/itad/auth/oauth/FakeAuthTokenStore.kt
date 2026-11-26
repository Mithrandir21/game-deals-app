package pm.bam.gamedeals.remote.itad.auth.oauth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import pm.bam.gamedeals.domain.auth.AuthTokenStore
import pm.bam.gamedeals.domain.models.AuthState

internal class FakeAuthTokenStore(
    private var access: String? = null,
    private var refresh: String? = null,
    private var expiresAt: Long = 0L,
    private var username: String? = null,
    private var scopeVersion: Int = 0,
) : AuthTokenStore {
    var cleared = false
        private set

    override fun observeAuthState(): Flow<AuthState> =
        flowOf(username?.let { AuthState.LoggedIn(it) } ?: AuthState.LoggedOut)

    override suspend fun getAccessToken(): String? = access
    override suspend fun getRefreshToken(): String? = refresh
    override suspend fun getUsername(): String? = username
    override suspend fun getExpiresAtEpochMs(): Long = expiresAt
    override suspend fun getScopeVersion(): Int = scopeVersion

    override suspend fun saveTokens(accessToken: String, refreshToken: String, expiresAtEpochMs: Long, username: String, scopeVersion: Int) {
        access = accessToken
        refresh = refreshToken
        expiresAt = expiresAtEpochMs
        this.username = username
        this.scopeVersion = scopeVersion
    }

    override suspend fun updateUsername(username: String) { this.username = username }

    override suspend fun clear() {
        access = null
        refresh = null
        expiresAt = 0L
        username = null
        cleared = true
    }
}
