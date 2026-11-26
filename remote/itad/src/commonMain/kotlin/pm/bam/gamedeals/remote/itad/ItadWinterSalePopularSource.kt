package pm.bam.gamedeals.remote.itad

import com.skydoves.sandwich.getOrThrow
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSalePopularGame
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSalePopularSource
import pm.bam.gamedeals.remote.itad.api.ItadDealsApi
import pm.bam.gamedeals.remote.itad.models.RemoteItadDealsFilter
import pm.bam.gamedeals.remote.itad.models.RemoteItadDealsRequest
import pm.bam.gamedeals.remote.itad.models.toGameArtwork

internal class ItadWinterSalePopularSource(private val api: ItadDealsApi) : WinterSalePopularSource {
    override suspend fun fetch(country: String, includeAdult: Boolean, limit: Int): List<WinterSalePopularGame> =
        api.getDeals(
            RemoteItadDealsRequest(
                country = country,
                limit = limit,
                sort = "rank",
                mature = includeAdult,
                filter = RemoteItadDealsFilter(type = listOf(1)),
            ),
        ).getOrThrow().list.map { WinterSalePopularGame(it.id, it.title, it.assets.toGameArtwork(), it.type, it.mature) }
}
