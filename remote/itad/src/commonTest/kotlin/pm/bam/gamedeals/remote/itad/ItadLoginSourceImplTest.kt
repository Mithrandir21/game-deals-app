package pm.bam.gamedeals.remote.itad

import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.domain.auth.AuthTokenStore
import pm.bam.gamedeals.domain.auth.CURRENT_SCOPE_VERSION
import pm.bam.gamedeals.domain.models.AuthState
import pm.bam.gamedeals.domain.models.CollectionEntry
import pm.bam.gamedeals.domain.models.IgnoredEntry
import pm.bam.gamedeals.domain.models.ItadNote
import pm.bam.gamedeals.domain.models.ItadNotification
import pm.bam.gamedeals.domain.models.ItadUser
import pm.bam.gamedeals.domain.models.WaitlistEntry
import pm.bam.gamedeals.domain.source.ItadAccountSource
import pm.bam.gamedeals.remote.itad.auth.ItadCredentials
import pm.bam.gamedeals.remote.itad.auth.oauth.AuthBrowserLauncher
import pm.bam.gamedeals.remote.itad.auth.oauth.AuthRedirectResult
import pm.bam.gamedeals.remote.itad.auth.oauth.ItadOAuthClient
import pm.bam.gamedeals.testing.mockHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ItadLoginSourceImplTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val credentials = ItadCredentials("key", "client-123", "pm.bam.gamedeals://oauth/itad")
    private val clock = Clock { 1_000_000L }
    private val tokenJson = """{"access_token":"AT","token_type":"Bearer","expires_in":3600,"refresh_token":"RT"}"""

    private fun oauthClient() = ItadOAuthClient(
        mockHttpClient(json) { _ -> respond(tokenJson, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) },
        credentials,
    )

    private fun source(launcher: AuthBrowserLauncher, store: AuthTokenStore, user: ItadUser = ItadUser("alice")) =
        ItadLoginSourceImpl(oauthClient(), launcher, FakeAccountSource(user), store, credentials, clock)

    @Test
    fun login_success_exchanges_code_and_persists_username() = runTest {
        val store = RecordingAuthTokenStore()
        val user = source(EchoStateLauncher(code = "the-code"), store).login()

        assertEquals("alice", user?.username)
        assertEquals("AT", store.lastAccessToken)
        assertEquals("RT", store.lastRefreshToken)
        assertEquals("alice", store.lastUsername) // re-saved with the real username
        assertEquals(1_000_000L + 3600 * 1000L, store.lastExpiresAt)
        assertEquals(CURRENT_SCOPE_VERSION, store.lastScopeVersion) // fresh login stamps the current scope set
        assertEquals(2, store.saveCount) // provisional (blank username) + final
    }

    @Test
    fun login_cancelled_returns_null_and_persists_nothing() = runTest {
        val store = RecordingAuthTokenStore()
        assertNull(source(CancelledLauncher, store).login())
        assertEquals(0, store.saveCount)
    }

    @Test
    fun login_state_mismatch_throws() = runTest {
        val store = RecordingAuthTokenStore()
        assertFailsWith<IllegalStateException> { source(WrongStateLauncher(code = "c"), store).login() }
    }

    /** Echoes the `state` from the authorize URL back, as a real provider would. */
    private class EchoStateLauncher(private val code: String) : AuthBrowserLauncher {
        override suspend fun authorize(authorizeUrl: String, redirectScheme: String): AuthRedirectResult =
            AuthRedirectResult.Success(code, authorizeUrl.substringAfter("state=").substringBefore("&"))
    }

    private object CancelledLauncher : AuthBrowserLauncher {
        override suspend fun authorize(authorizeUrl: String, redirectScheme: String): AuthRedirectResult =
            AuthRedirectResult.Cancelled
    }

    private class WrongStateLauncher(private val code: String) : AuthBrowserLauncher {
        override suspend fun authorize(authorizeUrl: String, redirectScheme: String): AuthRedirectResult =
            AuthRedirectResult.Success(code, "not-the-real-state")
    }

    private class FakeAccountSource(private val user: ItadUser) : ItadAccountSource {
        override suspend fun getUserInfo(): ItadUser = user
        override suspend fun getWaitlist(): List<WaitlistEntry> = emptyList()
        override suspend fun addToWaitlist(gameId: String) = Unit
        override suspend fun removeFromWaitlist(gameId: String) = Unit
        override suspend fun getCollection(): List<CollectionEntry> = emptyList()
        override suspend fun addToCollection(gameId: String) = Unit
        override suspend fun removeFromCollection(gameId: String) = Unit
        override suspend fun getNotifications(): List<ItadNotification> = emptyList()
        override suspend fun markNotificationRead(id: String) = Unit
        override suspend fun markAllNotificationsRead() = Unit
        override suspend fun getIgnored(): List<IgnoredEntry> = emptyList()
        override suspend fun addToIgnored(gameId: String) = Unit
        override suspend fun removeFromIgnored(gameId: String) = Unit
        override suspend fun getNotes(): List<ItadNote> = emptyList()
        override suspend fun setNote(gameId: String, note: String) = Unit
        override suspend fun removeNote(gameId: String) = Unit
    }

    private class RecordingAuthTokenStore : AuthTokenStore {
        var saveCount = 0; private set
        var lastAccessToken: String? = null; private set
        var lastRefreshToken: String? = null; private set
        var lastExpiresAt = 0L; private set
        var lastUsername: String? = null; private set
        var lastScopeVersion = -1; private set

        override fun observeAuthState(): Flow<AuthState> = flowOf(AuthState.LoggedOut)
        override suspend fun getAccessToken(): String? = lastAccessToken
        override suspend fun getRefreshToken(): String? = lastRefreshToken
        override suspend fun getUsername(): String? = lastUsername
        override suspend fun getExpiresAtEpochMs(): Long = lastExpiresAt
        override suspend fun getScopeVersion(): Int = lastScopeVersion
        override suspend fun saveTokens(accessToken: String, refreshToken: String, expiresAtEpochMs: Long, username: String, scopeVersion: Int) {
            saveCount++
            lastAccessToken = accessToken
            lastRefreshToken = refreshToken
            lastExpiresAt = expiresAtEpochMs
            lastUsername = username
            lastScopeVersion = scopeVersion
        }
        override suspend fun clear() = Unit
    }
}
