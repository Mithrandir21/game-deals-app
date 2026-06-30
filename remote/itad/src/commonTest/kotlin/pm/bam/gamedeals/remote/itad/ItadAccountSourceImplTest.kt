package pm.bam.gamedeals.remote.itad

import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import pm.bam.gamedeals.domain.models.thumbnail
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.remote.exceptions.RemoteExceptionTransformer
import pm.bam.gamedeals.remote.itad.api.ItadCollectionApi
import pm.bam.gamedeals.remote.itad.api.ItadIgnoredApi
import pm.bam.gamedeals.remote.itad.api.ItadNotesApi
import pm.bam.gamedeals.remote.itad.api.ItadNotificationsApi
import pm.bam.gamedeals.remote.itad.api.ItadUserApi
import pm.bam.gamedeals.remote.itad.api.ItadWaitlistApi
import pm.bam.gamedeals.testing.TestingLoggingListener
import pm.bam.gamedeals.testing.mockHttpClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
                    respond("""[{"id":"uuid-1","title":"Hades","type":"game","added":"2023-02-01T21:04:21+01:00","assets":{"boxart":"box.jpg","banner300":"banner300.jpg"}}]""", HttpStatusCode.OK, jsonHeaders)
                request.url.encodedPath == "/collection/games/v1" && request.method == HttpMethod.Get ->
                    respond("""[{"id":"uuid-2","title":"Celeste","type":"game","group":3,"added":"2022-05-10T00:00:00+00:00"}]""", HttpStatusCode.OK, jsonHeaders)
                request.url.encodedPath == "/notifications/v1" && request.method == HttpMethod.Get ->
                    respond("""[{"id":"n1","type":"waitlist","title":"Price drop","timestamp":"2026-06-12T00:00:00+00:00","read":null}]""", HttpStatusCode.OK, jsonHeaders)
                request.url.encodedPath == "/notifications/waitlist/v1" && request.method == HttpMethod.Get ->
                    // Unconsumed game fields (slug/type/mature/lastPrice) and deal fields (drm/platforms/
                    // timestamp/expiry) are present on the wire and must be ignored; deals + historyLow are mapped.
                    respond(
                        """{"id":"n1","timestamp":"2026-06-12T00:00:00+00:00","read":null,"games":[{"id":"uuid-9","slug":"hades","title":"Hades","type":"game","mature":false,"historyLow":{"amount":6.99,"amountInt":699,"currency":"EUR"},"lastPrice":{"amount":13.99,"amountInt":1399,"currency":"EUR"},"deals":[{"shop":{"id":35,"name":"GOG"},"price":{"amount":17.09,"amountInt":1709,"currency":"EUR"},"regular":{"amount":59.99,"amountInt":5999,"currency":"EUR"},"cut":72,"voucher":"PROMO","storeLow":{"amount":7,"amountInt":700,"currency":"EUR"},"flag":"N","drm":[{"id":1,"name":"Steam"}],"platforms":[{"id":1,"name":"Windows"}],"timestamp":"2024-02-11T01:20:50+01:00","expiry":null,"url":"https://itad.link/abc/"}]}]}""",
                        HttpStatusCode.OK,
                        jsonHeaders,
                    )
                request.url.encodedPath == "/ignored/games/v1" && request.method == HttpMethod.Get ->
                    respond("""[{"id":"uuid-3","title":"Untitled Goose Game"}]""", HttpStatusCode.OK, jsonHeaders)
                request.url.encodedPath == "/user/notes/v1" && request.method == HttpMethod.Get ->
                    respond("""[{"gid":"uuid-4","note":"Wait for a deeper sale"}]""", HttpStatusCode.OK, jsonHeaders)
                else -> respond("", HttpStatusCode.NoContent) // PUT/DELETE writes
            }
        }
        return ItadAccountSourceImpl(
            logger = logger,
            userApi = ItadUserApi(client),
            waitlistApi = ItadWaitlistApi(client),
            collectionApi = ItadCollectionApi(client),
            notificationsApi = ItadNotificationsApi(client),
            ignoredApi = ItadIgnoredApi(client),
            notesApi = ItadNotesApi(client),
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
        assertEquals("banner300.jpg", entries.single().artwork.thumbnail) // prioritized banner300 over boxart
        assertEquals("game", entries.single().type)
        // "added" (ISO offset) is parsed to epoch millis: 2023-02-01T21:04:21+01:00 == 2023-02-01T20:04:21Z.
        assertEquals(1675281861000L, entries.single().addedEpochMs)
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
        assertEquals(3, entries.single().group)
        assertEquals(1652140800000L, entries.single().addedEpochMs) // 2022-05-10T00:00:00Z
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

    @Test
    fun getWaitlistNotificationGames_parses_games_and_passes_id() = runTest {
        val games = source().getWaitlistNotificationGames("n1")
        assertEquals("uuid-9", games.single().gameId)
        assertEquals("Hades", games.single().title)
        assertEquals("/notifications/waitlist/v1", recorded.single().url.encodedPath)
        assertEquals("n1", recorded.single().url.parameters["id"])
    }

    @Test
    fun getWaitlistNotificationDetail_maps_deals_history_low_and_flags() = runTest {
        val detail = source().getWaitlistNotificationDetail("n1")

        assertEquals("n1", detail.notificationId)
        val game = detail.games.single()
        assertEquals("uuid-9", game.gameId)
        assertEquals("Hades", game.title)
        assertFalse(game.isExpired)
        assertNotNull(game.historicalLowDenominated) // historyLow mapped + formatted

        val deal = game.bestDeal!!
        assertEquals("GOG", deal.shopName)
        assertEquals(72, deal.cutPercent)
        assertTrue(deal.isNewHistoricalLow) // flag "N"
        assertTrue(deal.hasVoucher)         // non-blank voucher
        assertFalse(deal.isStoreLow)        // flag is "N", not "S"

        assertEquals("/notifications/waitlist/v1", recorded.single().url.encodedPath)
        assertEquals("n1", recorded.single().url.parameters["id"])
    }

    @Test
    fun getIgnored_maps_games_to_entries() = runTest {
        val entries = source().getIgnored()
        assertEquals("uuid-3", entries.single().gameId)
        assertEquals("Untitled Goose Game", entries.single().title)
        assertEquals("/ignored/games/v1", recorded.single().url.encodedPath)
    }

    @Test
    fun addToIgnored_puts_to_ignored_endpoint() = runTest {
        source().addToIgnored("uuid-3")
        assertEquals(HttpMethod.Put, recorded.single().method)
        assertEquals("/ignored/games/v1", recorded.single().url.encodedPath)
    }

    @Test
    fun removeFromIgnored_deletes_from_ignored_endpoint() = runTest {
        source().removeFromIgnored("uuid-3")
        assertEquals(HttpMethod.Delete, recorded.single().method)
        assertEquals("/ignored/games/v1", recorded.single().url.encodedPath)
    }

    @Test
    fun getNotes_maps_to_entries() = runTest {
        val notes = source().getNotes()
        assertEquals("uuid-4", notes.single().gameId)
        assertEquals("Wait for a deeper sale", notes.single().note)
        assertEquals("/user/notes/v1", recorded.single().url.encodedPath)
    }

    @Test
    fun setNote_puts_to_notes_endpoint() = runTest {
        source().setNote("uuid-4", "Buy at <$20")
        assertEquals(HttpMethod.Put, recorded.single().method)
        assertEquals("/user/notes/v1", recorded.single().url.encodedPath)
    }

    @Test
    fun removeNote_deletes_from_notes_endpoint() = runTest {
        source().removeNote("uuid-4")
        assertEquals(HttpMethod.Delete, recorded.single().method)
        assertEquals("/user/notes/v1", recorded.single().url.encodedPath)
    }
}
