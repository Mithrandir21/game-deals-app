package pm.bam.gamedeals.remote.itad

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleDealFlag
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleMoney
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleShelf
import pm.bam.gamedeals.remote.exceptions.RemoteExceptionTransformer
import pm.bam.gamedeals.remote.itad.api.ItadDealsApi
import pm.bam.gamedeals.remote.itad.api.ItadShopsApi
import pm.bam.gamedeals.remote.itad.logic.itadHttpClient
import pm.bam.gamedeals.remote.logic.RemoteBuildType
import pm.bam.gamedeals.remote.logic.RemoteBuildUtil
import pm.bam.gamedeals.testing.TestingLoggingListener
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ItadWinterSaleHubSourceTest {

    private val bodies = mutableListOf<String>()

    private fun client(reply: String, status: HttpStatusCode = HttpStatusCode.OK) = itadHttpClient(
        json = Json { ignoreUnknownKeys = true },
        buildUtil = RemoteBuildUtil { RemoteBuildType.RELEASE },
        apiKey = "test-key",
        engine = MockEngine { request -> record(request); respond(reply, status, headersOf(HttpHeaders.ContentType, "application/json")) },
    )

    private fun MockRequestHandleScope.record(request: HttpRequestData) {
        (request.body as? TextContent)?.let { bodies += it.text }
    }

    private fun source(client: HttpClient) =
        ItadWinterSaleHubSource(ItadDealsApi(client), ItadShopsApi(client), TestingLoggingListener(), RemoteExceptionTransformer { it })

    private fun sentFilter() = Json.parseToJsonElement(bodies.single()).jsonObject

    @Test
    fun store_shelves_ask_for_that_shop_sorted_by_popularity_in_the_body() = runTest {
        val client = client(RECORDED_ALL_TIME_LOWS_GB)

        source(client).deals(WinterSaleShelf.Store(61), "GB", includeAdult = false, offset = 0, limit = 10)

        val body = sentFilter()
        assertEquals("rank", body["sort"]?.jsonPrimitive?.content, "sort only works in the POST body")
        assertEquals("GB", body["country"]?.jsonPrimitive?.content)
        assertEquals(listOf(61), body["shops"]?.jsonArray?.map { it.jsonPrimitive.int })
        assertEquals("false", body["mature"]?.jsonPrimitive?.content)
        assertEquals(10, body["limit"]?.jsonPrimitive?.int)
        assertNull(body["offset"], "the first page sends no offset")
        val filter = body["filter"]!!.jsonObject
        assertEquals(listOf(1), filter["type"]?.jsonArray?.map { it.jsonPrimitive.int }, "full games only")
        assertNull(filter["flag"])
        assertNull(filter["cut"])
        client.close()
    }

    @Test
    fun all_time_lows_filter_on_the_low_flag_across_every_store() = runTest {
        val client = client(RECORDED_ALL_TIME_LOWS_GB)

        source(client).deals(WinterSaleShelf.AllTimeLows, "GB", includeAdult = true, offset = 30, limit = 30)

        val body = sentFilter()
        assertNull(body["shops"])
        assertEquals("true", body["mature"]?.jsonPrimitive?.content)
        assertEquals(30, body["offset"]?.jsonPrimitive?.int)
        assertEquals("H", body["filter"]!!.jsonObject["flag"]?.jsonPrimitive?.content)
        client.close()
    }

    @Test
    fun big_cuts_ask_for_seventy_five_percent_or_more() = runTest {
        val client = client(RECORDED_ALL_TIME_LOWS_GB)

        source(client).deals(WinterSaleShelf.BigCuts, "GB", includeAdult = false, offset = 0, limit = 12)

        val cut = sentFilter()["filter"]!!.jsonObject["cut"]!!.jsonObject
        assertEquals(75, cut["min"]?.jsonPrimitive?.int)
        assertEquals("null", cut["max"].toString())
        client.close()
    }

    @Test
    fun a_real_reply_maps_prices_flags_artwork_and_the_next_page() = runTest {
        val client = client(RECORDED_ALL_TIME_LOWS_GB)

        val page = source(client).deals(WinterSaleShelf.AllTimeLows, "GB", includeAdult = false, offset = 0, limit = 3)

        assertEquals(listOf("Metro Exodus", "Outlast", "Noita"), page.deals.map { it.title })
        val metro = page.deals.first()
        assertEquals(16, metro.shopId)
        assertEquals("Epic Game Store", metro.shopName)
        assertEquals(WinterSaleMoney(2.49, "GBP"), metro.price)
        assertEquals(WinterSaleMoney(24.99, "GBP"), metro.regular)
        assertEquals(90, metro.cutPercent)
        assertEquals(WinterSaleDealFlag.AllTimeLow, metro.flag)
        assertTrue(metro.isBigCut)
        assertFalse(metro.mature)
        assertTrue(metro.artwork.boxart!!.endsWith("boxart.jpg?t=1786477819"))
        assertTrue(metro.url.startsWith("https://itad.link/"))
        assertEquals(3, page.nextOffset)
        client.close()
    }

    @Test
    fun the_last_page_has_no_next_offset() = runTest {
        val client = client("""{"nextOffset":40,"hasMore":false,"list":[]}""")

        val page = source(client).deals(WinterSaleShelf.BigCuts, "GB", includeAdult = false, offset = 30, limit = 30)

        assertTrue(page.deals.isEmpty())
        assertNull(page.nextOffset)
        client.close()
    }

    @Test
    fun a_real_shop_list_gives_each_shop_its_deal_count() = runTest {
        val client = client(RECORDED_SHOPS_GB)

        val counts = source(client).shopCounts("GB").associateBy { it.shopId }

        assertEquals(33646, counts.getValue(61).deals)
        assertEquals("Steam", counts.getValue(61).name)
        assertEquals(538, counts.getValue(16).deals)
        assertEquals("GamesPlanet UK", counts.getValue(26).name)
        client.close()
    }

    @Test
    fun a_sparse_deal_without_a_regular_price_or_artwork_still_maps() = runTest {
        val client = client(
            """{"nextOffset":1,"hasMore":true,"list":[{"id":"g1","title":"Late Night","mature":true,
            "deal":{"shop":{"id":61,"name":"Steam"},"price":{"amount":1.0,"amountInt":100,"currency":"GBP"},"cut":10,"url":"https://itad.link/g1"}}]}""",
        )

        val deal = source(client).deals(WinterSaleShelf.Store(61), "GB", includeAdult = true, offset = 0, limit = 1).deals.single()

        assertNull(deal.regular)
        assertNull(deal.flag)
        assertNull(deal.artwork.boxart)
        assertTrue(deal.mature)
        assertFalse(deal.isBigCut)
        client.close()
    }

    @Test
    fun a_shop_without_a_deal_count_counts_as_none() = runTest {
        val client = client("""[{"id":61,"title":"Steam","deals":12},{"id":35,"title":"GOG"}]""")

        val counts = source(client).shopCounts("GB").associateBy { it.shopId }

        assertEquals(12, counts.getValue(61).deals)
        assertEquals(0, counts.getValue(35).deals)
        client.close()
    }

    @Test
    fun a_failed_call_throws_so_the_hub_can_hide_that_shelf() = runTest {
        val client = client("""{"error":"down"}""", HttpStatusCode.ServiceUnavailable)

        assertFailsWith<Throwable> { source(client).deals(WinterSaleShelf.BigCuts, "GB", includeAdult = false, offset = 0, limit = 12) }
        client.close()
    }

    @Test
    fun only_the_three_known_flags_become_badges() {
        assertEquals(WinterSaleDealFlag.NewLow, itadFlag("N"))
        assertEquals(WinterSaleDealFlag.AllTimeLow, itadFlag("H"))
        assertEquals(WinterSaleDealFlag.StoreLow, itadFlag("S"))
        assertNull(itadFlag(null))
        assertNull(itadFlag("X"))
    }
}
