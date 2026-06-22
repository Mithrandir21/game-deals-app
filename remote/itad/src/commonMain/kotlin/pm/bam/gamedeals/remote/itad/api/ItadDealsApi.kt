package pm.bam.gamedeals.remote.itad.api

import com.skydoves.sandwich.ApiResponse
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.remote.itad.models.RemoteItadDealsGame
import pm.bam.gamedeals.remote.itad.models.RemoteItadDealsRequest
import pm.bam.gamedeals.remote.itad.models.RemoteItadDealsResponse
import pm.bam.gamedeals.remote.logic.decodeListSkippingInvalid

/**
 * ITAD deals list endpoint. The live `/deals/v2` wraps the games in an envelope
 * `{ nextOffset, hasMore, list }`, and each list item is a game with a single best `deal` (confirmed
 * against the live API during Phase 2b).
 *
 * Issued as a **POST** with a JSON body ([RemoteItadDealsRequest]): only the POST body honours ITAD's
 * rich `filter` object (and `shops`/`sort`/`mature`/paging) server-side — the GET query string ignores
 * the structured filters. The response shape is unchanged from the GET variant.
 *
 * The `list` is decoded **element by element** so one malformed deal row drops that row instead of
 * losing the whole page. [json]/[logger] default for tests; DI injects the configured singletons.
 */
class ItadDealsApi(
    private val httpClient: HttpClient,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
    private val logger: Logger? = null,
) {

    suspend fun getDeals(request: RemoteItadDealsRequest): ApiResponse<RemoteItadDealsResponse> = try {
        val text = httpClient.post("/deals/v2") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.bodyAsText()

        val root = json.parseToJsonElement(text).jsonObject
        val list = root["list"]?.jsonArray
            ?.let { json.decodeListSkippingInvalid(it, RemoteItadDealsGame.serializer(), logger, TAG) }
            ?: emptyList()

        ApiResponse.Success(
            RemoteItadDealsResponse(
                nextOffset = root["nextOffset"]?.jsonPrimitive?.intOrNull,
                hasMore = root["hasMore"]?.jsonPrimitive?.booleanOrNull ?: false,
                list = list,
            ),
        )
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        ApiResponse.exception(t)
    }

    private companion object {
        private const val TAG = "ItadDealsApi"
    }
}
