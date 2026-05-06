package pm.bam.gamedeals.remote.cheapshark.api

import com.skydoves.sandwich.ApiResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.CancellationException
import pm.bam.gamedeals.remote.cheapshark.api.models.deals.RemoteDealsQuery
import pm.bam.gamedeals.remote.cheapshark.api.models.deals.RemoteDealsSortBy
import pm.bam.gamedeals.remote.cheapshark.models.RemoteDeal
import pm.bam.gamedeals.remote.cheapshark.models.RemoteDealDetails

/**
 * CheapShark deals endpoints. The HttpClient is configured with `expectSuccess = true`
 * (see RemoteNetworkModule), so 4xx/5xx throws Ktor's `ResponseException`; the
 * RemoteExceptionTransformer maps that to the `RemoteHttpException` taxonomy.
 *
 * The try/catch wrapper is intentional: sandwich's `responseOf` helper isn't
 * suspend-aware in 2.x, so we wrap manually. `CancellationException` is rethrown
 * to preserve coroutine cancellation semantics.
 *
 * Ktor's `parameter("name", value)` helper drops null values, matching Retrofit's
 * `@Query` semantics — no `if (x != null) parameter(...)` guards required.
 */
class DealsApi(private val httpClient: HttpClient) {

    suspend fun getDeals(query: RemoteDealsQuery = RemoteDealsQuery()): ApiResponse<List<RemoteDeal>> = try {
        ApiResponse.Success(
            httpClient.get("/api/1.0/deals") {
                parameter("storeID", query.storeID)
                parameter("pageNumber", query.pageNumber)
                parameter("pageSize", query.pageSize)
                parameter("sortBy", query.sortBy?.toApiString())
                parameter("desc", query.desc)
                parameter("lowerPrice", query.lowerPrice)
                parameter("upperPrice", query.upperPrice)
                parameter("metacritic", query.metacritic)
                parameter("steamRating", query.steamRating)
                parameter("maxAge", query.maxAge)
                parameter("steamAppID", query.steamAppID)
                parameter("title", query.title)
                parameter("exact", query.exact)
                parameter("AAA", query.aaa)
                parameter("steamworks", query.steamworks)
                parameter("onSale", query.onSale)
            }.body<List<RemoteDeal>>()
        )
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        ApiResponse.exception(t)
    }

    suspend fun getDeal(id: String): ApiResponse<RemoteDealDetails> = try {
        ApiResponse.Success(
            // Cheapshark serves dealIDs already percent-encoded ("...%3D"). `parameter()`
            // would encode again ("...%253D") so the lookup 404s. Mirrors the pre-Ktor
            // `@Query("id", encoded = true)` semantic.
            httpClient.get("/api/1.0/deals") { url { encodedParameters.append("id", id) } }
                .body<RemoteDealDetails>()
        )
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        ApiResponse.exception(t)
    }
}

/**
 * Retrofit + kotlinx-serialization-converter previously used the enum's `@SerialName`
 * value (e.g. "DealRating") for query encoding. Ktor's `parameter()` calls `toString()`,
 * which returns the enum name (e.g. "DEALRATING"). Hardcoding the mapping keeps the
 * wire format identical to the Retrofit era.
 */
private fun RemoteDealsSortBy.toApiString(): String = when (this) {
    RemoteDealsSortBy.DEALRATING -> "DealRating"
    RemoteDealsSortBy.TITLE -> "Title"
    RemoteDealsSortBy.SAVINGS -> "Savings"
    RemoteDealsSortBy.PRICE -> "Price"
    RemoteDealsSortBy.METACRITIC -> "Metacritic"
    RemoteDealsSortBy.REVIEWS -> "Reviews"
    RemoteDealsSortBy.RELEASE -> "Release"
    RemoteDealsSortBy.STORE -> "Store"
    RemoteDealsSortBy.RECENT -> "Recent"
}
