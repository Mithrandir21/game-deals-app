package pm.bam.gamedeals.remote.wintersale

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.fromHttpToGmtDate
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleEventPayload
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleEventSource

internal class WinterSaleEventHttpSource(private val client: HttpClient) : WinterSaleEventSource {
    override suspend fun fetch(testDocument: Boolean): WinterSaleEventPayload {
        val response = client.get(if (testDocument) TEST_PATH else PATH) {
            header(HttpHeaders.CacheControl, "no-cache, no-store")
            header("Pragma", "no-cache")
        }
        val body = response.bodyAsText()
        require(body.length <= MAX_DOCUMENT_CHARS)
        val serverDate = response.headers[HttpHeaders.Date]?.let { runCatching { it.fromHttpToGmtDate().timestamp }.getOrNull() }
        return WinterSaleEventPayload(body, serverDate)
    }

    private companion object {
        const val MAX_DOCUMENT_CHARS = 16_384
        const val PATH = "/events/winter-sale-2026.json"
        const val TEST_PATH = "/events/winter-sale-2026-test.json"
    }
}
