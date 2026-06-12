package pm.bam.gamedeals.remote.itad.auth.oauth

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
import pm.bam.gamedeals.domain.models.AuthState
import pm.bam.gamedeals.remote.itad.auth.ItadCredentials
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.mockHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ItadTokenProviderTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val credentials = ItadCredentials("key", "client-123", "pm.bam.gamedeals://oauth/itad")
    private val clock = Clock { 1_000_000L }
    private val logger = TestingLoggingListener()

    @Test
    fun refresh_success_persists_new_tokens_and_preserves_username() = runTest {
        val store = FakeAuthTokenStore(refresh = "old-refresh", username = "bob", scopeVersion = 7)
        val oauth = ItadOAuthClient(
            mockHttpClient(json) { _ ->
                respond(
                    """{"access_token":"NEW","token_type":"Bearer","expires_in":7200,"refresh_token":"NEW-RT"}""",
                    HttpStatusCode.OK,
                    headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
            credentials,
        )
        val provider = ItadTokenProvider(store, oauth, clock, logger)

        val result = provider.refresh()

        assertEquals("NEW", result?.accessToken)
        assertEquals("NEW-RT", result?.refreshToken)
        assertEquals("NEW", store.getAccessToken())
        assertEquals("NEW-RT", store.getRefreshToken())
        assertEquals(1_000_000L + 7200 * 1000L, store.getExpiresAtEpochMs())
        assertEquals("bob", store.getUsername()) // preserved across refresh
        assertEquals(7, store.getScopeVersion()) // refresh grants no new scopes — scope version preserved
    }

    @Test
    fun refresh_without_a_stored_refresh_token_clears_and_returns_null() = runTest {
        val store = FakeAuthTokenStore(refresh = null)
        val oauth = ItadOAuthClient(mockHttpClient(json) { _ -> respond("") }, credentials)

        val result = ItadTokenProvider(store, oauth, clock, logger).refresh()

        assertNull(result)
        assertTrue(store.cleared)
    }

    @Test
    fun refresh_failure_clears_session() = runTest {
        val store = FakeAuthTokenStore(refresh = "old-refresh", username = "bob")
        // expectSuccess=true ⇒ a 400 throws inside refreshToken; the provider must catch + clear.
        val oauth = ItadOAuthClient(
            mockHttpClient(json) { _ -> respond("nope", HttpStatusCode.BadRequest) },
            credentials,
        )

        val result = ItadTokenProvider(store, oauth, clock, logger).refresh()

        assertNull(result)
        assertTrue(store.cleared)
    }

    private class FakeAuthTokenStore(
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

        override suspend fun clear() {
            access = null
            refresh = null
            expiresAt = 0L
            username = null
            cleared = true
        }
    }
}
