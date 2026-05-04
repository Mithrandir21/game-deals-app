package pm.bam.gamedeals.integration.support

import androidx.test.platform.app.InstrumentationRegistry
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

/**
 * Ktor [io.ktor.client.engine.mock.MockEngine] request handler used by instrumented tests.
 *
 * Maps inbound paths to JSON fixtures under `androidTest/assets/fixtures/` so the app can
 * run end-to-end against a known, hermetic backend. Unmapped paths return 404. Path-keyed
 * and stateless, so the same handler safely fronts both the CheapShark and GamerPower
 * MockEngines (the per-host base URL distinguishes them in the request URL, but routes
 * here only key on the path).
 */
object FixtureRequestHandler {

    fun MockRequestHandleScope.handle(request: HttpRequestData): io.ktor.client.request.HttpResponseData {
        val path = request.url.encodedPath
        val params = request.url.parameters

        val fixture = when {
            path.startsWith("/api/1.0/stores") -> "stores.json"
            path.startsWith("/api/1.0/deals") && params["id"] != null -> "deal_${params["id"]}.json"
            path.startsWith("/api/1.0/deals") && params["storeID"] == "1" ->
                if (params["pageSize"] == "10") "deals_storeid_1.json" else "deals_storeid_1_paged.json"
            path.startsWith("/api/other/releases") -> "releases.json"
            path.startsWith("/api/giveaways") -> "giveaways.json"
            else -> null
        } ?: return respondError(HttpStatusCode.NotFound)

        val body = InstrumentationRegistry.getInstrumentation().context.assets
            .open("fixtures/$fixture")
            .bufferedReader()
            .use { it.readText() }

        return respond(
            content = body,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }
}
