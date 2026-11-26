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
 * and stateless. (CheapShark routes were removed in Phase 4, epic #205; the GamerPower
 * giveaways route remains.)
 */
object FixtureRequestHandler {

    fun MockRequestHandleScope.handle(request: HttpRequestData): io.ktor.client.request.HttpResponseData {
        val path = request.url.encodedPath

        val fixture = when {
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
