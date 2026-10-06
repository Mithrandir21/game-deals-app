package pm.bam.gamedeals.remote.itad.logic

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import pm.bam.gamedeals.common.time.Clock
import pm.bam.gamedeals.remote.itad.auth.ItadCredentials
import pm.bam.gamedeals.remote.itad.auth.oauth.FakeAuthTokenStore
import pm.bam.gamedeals.remote.itad.auth.oauth.ItadOAuthClient
import pm.bam.gamedeals.remote.itad.auth.oauth.ItadTokenProvider
import pm.bam.gamedeals.remote.logic.RemoteBuildType
import pm.bam.gamedeals.remote.logic.RemoteBuildUtil
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.mockHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals

class ItadAuthHttpClientTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun every_request_carries_the_currently_stored_token_across_logout_and_account_switch() = runTest {
        val store = FakeAuthTokenStore(access = "TOKEN-A", refresh = "RT-A", username = "alice")
        val sentAuthorization = mutableListOf<String?>()
        val engine = MockEngine { request ->
            sentAuthorization += request.headers[HttpHeaders.Authorization]
            respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        val oauth = ItadOAuthClient(mockHttpClient(json) { _ -> respond("") }, ItadCredentials("key", "client", "scheme://cb"))
        val provider = ItadTokenProvider(store, oauth, Clock { 0L }, TestingLoggingListener())
        val client = itadAuthHttpClient(json, RemoteBuildUtil { RemoteBuildType.RELEASE }, provider, engine)

        client.get("/user/info/v2")
        store.clear()
        client.get("/user/info/v2")
        store.saveTokens("TOKEN-B", "RT-B", expiresAtEpochMs = 0L, username = "bob", scopeVersion = 0)
        client.get("/user/info/v2")

        assertEquals(listOf("Bearer TOKEN-A", null, "Bearer TOKEN-B"), sentAuthorization)
    }
}
