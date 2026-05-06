package pm.bam.gamedeals.remote.cheapshark.api

import com.skydoves.sandwich.ApiResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.coroutines.CancellationException
import pm.bam.gamedeals.remote.cheapshark.api.models.deals.RemoteDealsSortBy
import pm.bam.gamedeals.remote.cheapshark.models.RemoteDeal
import pm.bam.gamedeals.remote.cheapshark.models.RemoteDealDetails

class DealsApi(private val httpClient: HttpClient) {

    @Suppress("LongParameterList")
    suspend fun getDeals(
        storeID: Int? = null,
        pageNumber: Int? = null,
        pageSize: Int? = null,
        sortBy: RemoteDealsSortBy? = null,
        desc: Int? = null,
        lowerPrice: Int? = null,
        upperPrice: Int? = null,
        metacritic: Int? = null,
        steamRating: Int? = null,
        maxAge: Int? = null,
        steamAppID: Int? = null,
        title: String? = null,
        exact: Int? = null,
        aaa: Int? = null,
        steamworks: Int? = null,
        onSale: Int? = null
    ): ApiResponse<List<RemoteDeal>> = try {
        ApiResponse.Success(
            httpClient.get("/api/1.0/deals") {
                parameter("storeID", storeID)
                parameter("pageNumber", pageNumber)
                parameter("pageSize", pageSize)
                parameter("sortBy", sortBy?.toApiString())
                parameter("desc", desc)
                parameter("lowerPrice", lowerPrice)
                parameter("upperPrice", upperPrice)
                parameter("metacritic", metacritic)
                parameter("steamRating", steamRating)
                parameter("maxAge", maxAge)
                parameter("steamAppID", steamAppID)
                parameter("title", title)
                parameter("exact", exact)
                parameter("AAA", aaa)
                parameter("steamworks", steamworks)
                parameter("onSale", onSale)
            }.body<List<RemoteDeal>>()
        )
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        ApiResponse.exception(t)
    }

    suspend fun getDeal(id: String): ApiResponse<RemoteDealDetails> = try {
        ApiResponse.Success(
            // Cheapshark serves dealIDs already percent-encoded; `parameter()` would re-encode
            // and the lookup 404s. Use `encodedParameters` to skip the second encode pass.
            httpClient.get("/api/1.0/deals") { url { encodedParameters.append("id", id) } }
                .body<RemoteDealDetails>()
        )
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        ApiResponse.exception(t)
    }
}

// Wire format must stay PascalCase; Ktor's `parameter()` calls `toString()` (= UPPER enum name).
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
