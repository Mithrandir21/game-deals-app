package pm.bam.gamedeals.remote.igdb.auth

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.remote.logic.RemoteBuildType
import pm.bam.gamedeals.remote.logic.RemoteBuildUtil
import pm.bam.gamedeals.remote.logic.gameDealsHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Covers the persistence half of [IgdbTokenProvider] — the cold-start read, the expiry rule, and the
 * write-back on a fresh grant. The end-to-end Auth-plugin wiring is covered by `IgdbAuthChainTest`.
 */
class IgdbTokenProviderTest {

    @Test
    fun cold_start_with_a_persisted_in_date_token_serves_it_without_a_grant() = runTest {
        val store = InMemoryIgdbTokenStore(StoredIgdbToken(accessToken = "persisted", expiresAtEpochMs = NOW + 1))
        val recorded = mutableListOf<HttpRequestData>()
        val provider = provider(store, recorded)

        val tokens = provider.cachedTokens()

        assertNotNull(tokens)
        assertEquals("persisted", tokens.accessToken)
        assertTrue(recorded.isEmpty(), "A token still in date must not trigger a Twitch round-trip")
    }

    @Test
    fun cold_start_with_an_expired_token_reports_no_cached_token() = runTest {
        val store = InMemoryIgdbTokenStore(StoredIgdbToken(accessToken = "stale", expiresAtEpochMs = NOW))
        val provider = provider(store, mutableListOf())

        // Expiry is exclusive: at exactly expiresAtEpochMs the token is already spent, so the Auth
        // plugin falls through to its 401 -> refreshTokens path rather than sending a dead bearer.
        assertNull(provider.cachedTokens())
    }

    @Test
    fun an_empty_store_reports_no_cached_token() = runTest {
        val provider = provider(InMemoryIgdbTokenStore(), mutableListOf())

        assertNull(provider.cachedTokens())
    }

    @Test
    fun fetching_a_token_persists_it_with_an_expiry_derived_from_expires_in() = runTest {
        val store = InMemoryIgdbTokenStore()
        val provider = provider(store, mutableListOf())

        val tokens = provider.fetchToken()

        assertEquals(FRESH_TOKEN, tokens.accessToken)
        // `client_credentials` issues no refresh token; the grant itself is the renewal path.
        assertNull(tokens.refreshToken)

        val stored = assertNotNull(store.stored)
        assertEquals(FRESH_TOKEN, stored.accessToken)
        assertEquals(
            NOW + (EXPIRES_IN_SECONDS * 1000L) - IgdbTokenProvider.EXPIRY_SKEW_MILLIS,
            stored.expiresAtEpochMs,
        )
        assertEquals(1, store.saveCount)
    }

    @Test
    fun a_fetched_token_is_served_from_memory_without_re_reading_the_store() = runTest {
        val store = InMemoryIgdbTokenStore()
        val provider = provider(store, mutableListOf())

        provider.fetchToken()
        val loadsAfterFetch = store.loadCount
        val tokens = provider.cachedTokens()

        assertEquals(FRESH_TOKEN, assertNotNull(tokens).accessToken)
        assertEquals(loadsAfterFetch, store.loadCount, "The in-memory copy must satisfy the read")
    }

    private fun provider(store: IgdbTokenStore, recorded: MutableList<HttpRequestData>): IgdbTokenProvider {
        val engine = MockEngine { request ->
            recorded += request
            respond(
                content = """{"access_token":"$FRESH_TOKEN","expires_in":$EXPIRES_IN_SECONDS,"token_type":"bearer"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }
        val tokenClient: HttpClient = gameDealsHttpClient(
            json = Json { ignoreUnknownKeys = true },
            buildUtil = RemoteBuildUtil { RemoteBuildType.RELEASE },
            baseUrl = "https://id.twitch.tv",
            engine = engine,
        )
        return IgdbTokenProvider(
            tokenClient = tokenClient,
            credentials = IgdbCredentials(clientId = "fake-id", clientSecret = "fake-secret"),
            store = store,
            clock = Clock { NOW },
        )
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
        const val FRESH_TOKEN = "FRESH_TOKEN"
        const val EXPIRES_IN_SECONDS = 5_184_000L
    }
}
