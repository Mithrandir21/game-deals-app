package pm.bam.gamedeals.remote.cheapshark.api

import com.skydoves.sandwich.ApiResponse
import pm.bam.gamedeals.remote.cheapshark.api.models.deals.RemoteDealsSortBy
import pm.bam.gamedeals.remote.cheapshark.models.RemoteDeal
import pm.bam.gamedeals.remote.cheapshark.models.RemoteDealDetails
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query

interface DealsApi {

    @Suppress("kotlin:S107")
    @GET("/api/1.0/deals")
    @Headers("Accept: application/json")
    suspend fun getDeals(
        @Query("storeID") storeID: Int? = null,
        @Query("pageNumber") pageNumber: Int? = null,
        @Query("pageSize") pageSize: Int? = null,
        @Query("sortBy") sortBy: RemoteDealsSortBy? = null,
        @Query("desc") desc: Int? = null,
        @Query("lowerPrice") lowerPrice: Int? = null,
        @Query("upperPrice") upperPrice: Int? = null,
        @Query("metacritic") metacritic: Int? = null,
        @Query("steamRating") steamRating: Int? = null,
        @Query("maxAge") maxAge: Int? = null,
        @Query("steamAppID") steamAppID: Int? = null,
        @Query("title") title: String? = null,
        @Query("exact") exact: Int? = null,
        @Query("AAA") aaa: Int? = null,
        @Query("steamworks") steamworks: Int? = null,
        @Query("onSale") onSale: Int? = null
    ): ApiResponse<List<RemoteDeal>>

    @GET("/api/1.0/deals")
    @Headers("Accept: application/json")
    suspend fun getDeal(@Query("id", encoded = true) id: String): ApiResponse<RemoteDealDetails>

}