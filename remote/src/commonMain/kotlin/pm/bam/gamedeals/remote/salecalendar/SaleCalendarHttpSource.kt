package pm.bam.gamedeals.remote.salecalendar

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import pm.bam.gamedeals.domain.repositories.salecalendar.SaleCalendarSource

/** `GET https://gamedeals.bam.pm/events/sales.json` (`store/site/sales.json`): public data, nothing sent. */
internal class SaleCalendarHttpSource(private val client: HttpClient) : SaleCalendarSource {
    override suspend fun fetch(): String {
        val body = client.get(PATH).bodyAsText()
        require(body.length <= MAX_DOCUMENT_CHARS) { "sales.json is unexpectedly large" }
        return body
    }

    private companion object {
        const val PATH = "/events/sales.json"
        const val MAX_DOCUMENT_CHARS = 32_768
    }
}
