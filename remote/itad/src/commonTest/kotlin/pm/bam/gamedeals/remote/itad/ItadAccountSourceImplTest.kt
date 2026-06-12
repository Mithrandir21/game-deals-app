package pm.bam.gamedeals.remote.itad

import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.remote.exceptions.RemoteExceptionTransformer
import pm.bam.gamedeals.remote.itad.api.ItadCollectionApi
import pm.bam.gamedeals.remote.itad.api.ItadNotificationsApi
import pm.bam.gamedeals.remote.itad.api.ItadUserApi
import pm.bam.gamedeals.remote.itad.api.ItadWaitlistApi
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.mockHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * HTTP-level coverage for [ItadAccountSourceImpl] + the user `*Api` classes against a MockEngine
 * (epic #219, Phase 2.3). Routes by path/method; the bearer token attachment is the client builder's
 * concern and not exercised here.
 */
class ItadAccountSourceImplTest {

    private val logger: Logger = TestingLoggingListener()
    private val json = Json { ignoreUnknownKeys = true }
    private val recorded = mutableListOf<HttpRequestData>()

    private fun source(): ItadAccountSourceImpl {
        val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
        val client = mockHttpClient(json) { request ->
            recorded += request
            when {
                request.url.encodedPath == "/user/info/v2" -> respond("""{"username":"alice"}""", HttpStatusCode.OK, jsonHeaders)
                request.url.encodedPath == "/waitlist/games/v1" && request.method == HttpMethod.Get ->
                    respond("""[{"id":"uuid-1","title":"Hades","assets":{"boxart":"box.jpg","banner300":"banner300.jpg"}}]""", HttpStatusCode.OK, jsonHeaders)
                request.url.encodedPath == "/collection/games/v1" && request.method == HttpMethod.Get ->
                    respond("""[{"id":"uuid-2","title":"Celeste"}]""", HttpStatusCode.OK, jsonHeaders)
                request.url.encodedPath == "/notifications/v1" && request.method == HttpMethod.Get ->
                    respond("""[{"id":"n1","type":"waitlist","title":"Price drop","timestamp":"2026-06-12T00:00:00+00:00","read":null}]""", HttpStatusCode.OK, jsonHeaders)
                else -> respond("", HttpStatusCode.NoContent) // PUT/DELETE writes
            }
        }
        return ItadAccountSourceImpl(
            logger = logger,
            userApi = ItadUserApi(client),
            waitlistApi = ItadWaitlistApi(client),
            collectionApi = ItadCollectionApi(client),
            notificationsApi = ItadNotificationsApi(client),
            remoteExceptionTransformer = RemoteExceptionTransformer { it },
        )
    }

    @Test
    fun getUserInfo_parses_username() = runTest {
        val user = source().getUserInfo()
        assertEquals("alice", user.username)
        assertEquals("/user/info/v2", recorded.single().url.encodedPath)
    }

    @Test
    fun getWaitlist_maps_games_to_entries() = runTest {
        val entries = source().getWaitlist()
        assertEquals(1, entries.size)
        assertEquals("uuid-1", entries.single().gameId)
        assertEquals("Hades", entries.single().title)
        assertEquals("banner300.jpg", entries.single().boxart) // prioritized banner300 over boxart
        assertEquals("/waitlist/games/v1", recorded.single().url.encodedPath)
    }

    @Test
    fun addToWaitlist_puts_to_waitlist_endpoint() = runTest {
        source().addToWaitlist("uuid-1")
        assertEquals(HttpMethod.Put, recorded.single().method)
        assertEquals("/waitlist/games/v1", recorded.single().url.encodedPath)
    }

    @Test
    fun removeFromWaitlist_deletes_from_waitlist_endpoint() = runTest {
        source().removeFromWaitlist("uuid-1")
        assertEquals(HttpMethod.Delete, recorded.single().method)
        assertEquals("/waitlist/games/v1", recorded.single().url.encodedPath)
    }

    @Test
    fun getCollection_maps_games_to_entries() = runTest {
        val entries = source().getCollection()
        assertEquals("uuid-2", entries.single().gameId)
        assertEquals("Celeste", entries.single().title)
        assertEquals("/collection/games/v1", recorded.single().url.encodedPath)
    }

    @Test
    fun addToCollection_puts_to_collection_endpoint() = runTest {
        source().addToCollection("uuid-2")
        assertEquals(HttpMethod.Put, recorded.single().method)
        assertEquals("/collection/games/v1", recorded.single().url.encodedPath)
    }

    @Test
    fun getNotifications_maps_unread() = runTest {
        val list = source().getNotifications()
        assertEquals("n1", list.single().id)
        assertEquals("Price drop", list.single().title)
        assertFalse(list.single().read) // read=null ⇒ unread
        assertEquals("/notifications/v1", recorded.single().url.encodedPath)
    }

    @Test
    fun markNotificationRead_puts_with_id() = runTest {
        source().markNotificationRead("n1")
        assertEquals(HttpMethod.Put, recorded.single().method)
        assertEquals("/notifications/read/v1", recorded.single().url.encodedPath)
        assertEquals("n1", recorded.single().url.parameters["id"])
    }

    @Test
    fun markAllNotificationsRead_puts() = runTest {
        source().markAllNotificationsRead()
        assertEquals(HttpMethod.Put, recorded.single().method)
        assertEquals("/notifications/read/all/v1", recorded.single().url.encodedPath)
    }
}
