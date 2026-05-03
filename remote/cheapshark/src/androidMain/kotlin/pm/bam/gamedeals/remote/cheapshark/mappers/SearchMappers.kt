package pm.bam.gamedeals.remote.cheapshark.mappers

import kotlinx.serialization.ExperimentalSerializationApi
import pm.bam.gamedeals.domain.models.DealsSortBy
import pm.bam.gamedeals.domain.models.SearchParameters
import pm.bam.gamedeals.remote.cheapshark.api.models.deals.RemoteDealsQuery
import pm.bam.gamedeals.remote.cheapshark.api.models.deals.RemoteDealsSortBy

@OptIn(ExperimentalSerializationApi::class)
internal fun SearchParameters.toRemoteDealsQuery(): RemoteDealsQuery = RemoteDealsQuery(
    storeID = storeID,
    pageNumber = pageNumber,
    pageSize = pageSize,
    sortBy = sortBy?.toRemoteDealsSortBy(),
    desc = desc,
    lowerPrice = lowerPrice,
    upperPrice = upperPrice,
    metacritic = metacritic,
    steamRating = steamMinRating,
    maxAge = maxAge,
    steamAppID = steamAppID,
    title = title,
    exact = exact?.toInt(),
    aaa = aaa?.toInt(),
    steamworks = steamworks?.toInt(),
    onSale = onSale?.toInt()
)

internal fun DealsSortBy.toRemoteDealsSortBy(): RemoteDealsSortBy = when (this) {
    DealsSortBy.DEALRATING -> RemoteDealsSortBy.DEALRATING
    DealsSortBy.TITLE -> RemoteDealsSortBy.TITLE
    DealsSortBy.SAVINGS -> RemoteDealsSortBy.SAVINGS
    DealsSortBy.PRICE -> RemoteDealsSortBy.PRICE
    DealsSortBy.METACRITIC -> RemoteDealsSortBy.METACRITIC
    DealsSortBy.REVIEWS -> RemoteDealsSortBy.REVIEWS
    DealsSortBy.RELEASE -> RemoteDealsSortBy.RELEASE
    DealsSortBy.STORE -> RemoteDealsSortBy.STORE
    DealsSortBy.RECENT -> RemoteDealsSortBy.RECENT
}

private fun Boolean.toInt() = if (this) 1 else 0
