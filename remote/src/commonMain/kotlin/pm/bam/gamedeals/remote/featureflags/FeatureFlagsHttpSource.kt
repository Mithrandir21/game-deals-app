package pm.bam.gamedeals.remote.featureflags

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import pm.bam.gamedeals.domain.repositories.featureflags.FeatureFlagsSource

/** `GET https://gamedeals.bam.pm/api/flags/v1` (`store/site/flags-routes.mjs`): the app's remote switches. Nothing is sent. */
internal class FeatureFlagsHttpSource(private val client: HttpClient) : FeatureFlagsSource {
    override suspend fun fetch(): String {
        val body = client.get(PATH).bodyAsText()
        require(body.length <= MAX_DOCUMENT_CHARS) { "The switch file is unexpectedly large" }
        return body
    }

    private companion object {
        const val PATH = "/api/flags/v1"
        const val MAX_DOCUMENT_CHARS = 16_384
    }
}
