package pm.bam.gamedeals.remote.gamerpower.api

import com.skydoves.sandwich.ApiResponse
import pm.bam.gamedeals.remote.gamerpower.models.RemoteGiveaway
import retrofit2.http.GET

interface GamesApi {

    @GET("/api/giveaways")
    suspend fun getAllGames(): ApiResponse<List<RemoteGiveaway>>
}