package pm.bam.gamedeals.domain.auth

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.Json
import pm.bam.gamedeals.common.storage.Storage
import pm.bam.gamedeals.domain.models.AuthState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AuthTokenStoreImplTest {

    // In-memory [Storage] that actually (de)serializes with the given strategies, so the [StoredAuthToken]
    // round-trip — including the scopeVersion default for legacy blobs — is exercised faithfully.
    private val backing = mutableMapOf<String, String>()
    private val json = Json { ignoreUnknownKeys = true }
    private val storage = object : Storage {
        override suspend fun <T : Any> get(storageKey: String, deserializationStrategy: DeserializationStrategy<T>, defaultValue: T?): T =
            getNullable(storageKey, deserializationStrategy, defaultValue) ?: error("no value for $storageKey")

        override suspend fun <T : Any> getNullable(storageKey: String, deserializationStrategy: DeserializationStrategy<T>, defaultValue: T?): T? =
            backing[storageKey]?.let { json.decodeFromString(deserializationStrategy, it) } ?: defaultValue

        override suspend fun <T : Any> save(storageKey: String, data: T, serializationStrategy: SerializationStrategy<T>, overwrite: Boolean): Boolean {
            backing[storageKey] = json.encodeToString(serializationStrategy, data)
            return true
        }

        override suspend fun containsKey(storageKey: String): Boolean = backing.containsKey(storageKey)
        override suspend fun remove(storageKey: String): Boolean = backing.remove(storageKey) != null
    }

    private val store = AuthTokenStoreImpl(storage)

    @Test
    fun fresh_login_at_current_scope_version_does_not_need_reconnect() = runTest {
        store.saveTokens("AT", "RT", expiresAtEpochMs = 0L, username = "bob", scopeVersion = CURRENT_SCOPE_VERSION)

        val state = store.observeAuthState().first()
        assertIs<AuthState.LoggedIn>(state)
        assertEquals("bob", state.username)
        assertFalse(state.needsReconnect)
        assertEquals(CURRENT_SCOPE_VERSION, store.getScopeVersion())
    }

    @Test
    fun token_from_an_older_scope_version_needs_reconnect() = runTest {
        store.saveTokens("AT", "RT", expiresAtEpochMs = 0L, username = "bob", scopeVersion = CURRENT_SCOPE_VERSION - 1)

        val state = store.observeAuthState().first()
        assertIs<AuthState.LoggedIn>(state)
        assertTrue(state.needsReconnect)
    }

    @Test
    fun legacy_token_without_a_scope_version_field_needs_reconnect() = runTest {
        // A token persisted before scopeVersion existed (the original scope set) — no scopeVersion key.
        backing[AUTH_TOKEN_KEY] =
            """{"accessToken":"AT","refreshToken":"RT","expiresAtEpochMs":0,"username":"bob"}"""

        val state = store.observeAuthState().first()
        assertIs<AuthState.LoggedIn>(state)
        assertTrue(state.needsReconnect) // scopeVersion defaults to 0 < CURRENT ⇒ prompt reconnect
        assertEquals(0, store.getScopeVersion())
    }

    @Test
    fun clear_resets_to_logged_out() = runTest {
        store.saveTokens("AT", "RT", expiresAtEpochMs = 0L, username = "bob", scopeVersion = CURRENT_SCOPE_VERSION)
        store.clear()

        assertEquals(AuthState.LoggedOut, store.observeAuthState().first())
        assertFalse(backing.containsKey(AUTH_TOKEN_KEY))
    }
}
