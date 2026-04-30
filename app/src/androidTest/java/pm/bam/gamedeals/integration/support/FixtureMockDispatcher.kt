package pm.bam.gamedeals.integration.support

import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * `MockWebServer` dispatcher used by instrumented tests.
 *
 * Maps inbound HTTP paths to JSON fixtures under `androidTest/assets/fixtures/` so the
 * app can run end-to-end against a known, hermetic backend. Unmapped paths return 404.
 *
 * Hilt-built once per test (Singleton) and shared by both the CheapShark and GamerPower
 * `MockWebServer` instances — the route table is path-keyed and stateless, so one
 * dispatcher safely fronts both servers.
 */
@Singleton
class FixtureMockDispatcher @Inject constructor() : Dispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse {
        val path = request.path.orEmpty()

        // Route table: incoming path → fixture filename. Order matters where guards overlap
        // (e.g. `/api/1.0/deals?id=…` must match before the storeID branch).
        val fixture = when {
            path.startsWith("/api/1.0/stores") -> "stores.json"
            path.startsWith("/api/1.0/deals") && path.contains("id=") -> "deal_${idFromQuery(path)}.json"
            path.startsWith("/api/1.0/deals") && path.contains("storeID=1") ->
                if (path.contains("pageSize=10")) "deals_storeid_1.json" else "deals_storeid_1_paged.json"
            path.startsWith("/api/other/releases") -> "releases.json"
            path.startsWith("/api/giveaways") -> "giveaways.json"
            else -> null
        } ?: return MockResponse().setResponseCode(404)

        val body = InstrumentationRegistry.getInstrumentation().context.assets
            .open("fixtures/$fixture")
            .bufferedReader()
            .use { it.readText() }

        return MockResponse()
            .setResponseCode(200)
            .setBody(body)
    }

    private fun idFromQuery(path: String): String =
        path.substringAfter("id=").substringBefore('&')
}
