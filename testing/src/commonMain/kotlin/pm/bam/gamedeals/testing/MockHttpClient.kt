package pm.bam.gamedeals.testing

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Ktor [HttpClient] backed by a [MockEngine] with `expectSuccess = true` and
 * JSON content negotiation installed — the minimal shape every Ktor unit test
 * in this project needs. The [handler] is the same lambda shape MockEngine
 * accepts, so any test that previously hand-rolled `HttpClient(MockEngine { … })`
 * can drop in `mockHttpClient(json) { request -> … }`.
 */
fun mockHttpClient(
    json: Json,
    handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
): HttpClient = HttpClient(MockEngine { request -> handler(request) }) {
    expectSuccess = true
    install(ContentNegotiation) { json(json) }
}
