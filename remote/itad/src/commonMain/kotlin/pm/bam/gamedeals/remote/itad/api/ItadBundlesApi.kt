package pm.bam.gamedeals.remote.itad.api

import com.skydoves.sandwich.ApiResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.CancellationException
import pm.bam.gamedeals.remote.itad.models.RemoteItadBundle

/**
 * ITAD bundles endpoint (`/bundles/v1`, epic #205 Phase 3c). Returns a bare JSON array of active
 * bundles for the given [country]; `parameter(name, value)` drops nulls so optional params need no
 * guards. The HttpClient uses `expectSuccess = true`, so 4xx/5xx throw and are mapped downstream.
 */
class ItadBundlesApi(private val httpClient: HttpClient) {

    suspend fun getBundles(
        country: String? = null,
        limit: Int? = null,
        sort: String? = null,
    ): ApiResponse<List<RemoteItadBundle>> = try {
        ApiResponse.Success(
            httpClient.get("/bundles/v1") {
                parameter("country", country)
                parameter("limit", limit)
                parameter("sort", sort)
            }.body<List<RemoteItadBundle>>()
        )
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        ApiResponse.exception(t)
    }
}
