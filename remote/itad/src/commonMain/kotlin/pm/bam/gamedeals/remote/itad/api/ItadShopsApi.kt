package pm.bam.gamedeals.remote.itad.api

import com.skydoves.sandwich.ApiResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.CancellationException
import pm.bam.gamedeals.remote.itad.models.RemoteItadShop

/**
 * ITAD shops endpoint. The HttpClient is configured with `expectSuccess = true`, so 4xx/5xx throws
 * Ktor's `ResponseException`; the RemoteExceptionTransformer maps that to the app's exception taxonomy.
 * `parameter(name, value)` drops null values, so optional params need no null guards.
 *
 * NOTE: #207 cites `/services/shops/v1`; ITAD v2 actually serves `/service/shops/v1` (singular).
 * Confirm against the live API during integration (see the manual smoke step in the Phase 1 plan).
 */
class ItadShopsApi(private val httpClient: HttpClient) {

    suspend fun getShops(country: String? = null): ApiResponse<List<RemoteItadShop>> = try {
        ApiResponse.Success(
            httpClient.get("/service/shops/v1") {
                parameter("country", country)
            }.body<List<RemoteItadShop>>()
        )
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        ApiResponse.exception(t)
    }
}
