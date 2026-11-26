package pm.bam.gamedeals.remote.itad

import com.skydoves.sandwich.getOrThrow
import pm.bam.gamedeals.domain.repositories.wintersale.WINTER_SALE_BIG_CUT_PERCENT
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleDeal
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleDealFlag
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleDealPage
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleHubSource
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleMoney
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleShelf
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleShopCount
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.remote.exceptions.RemoteExceptionTransformer
import pm.bam.gamedeals.remote.itad.api.ItadDealsApi
import pm.bam.gamedeals.remote.itad.api.ItadShopsApi
import pm.bam.gamedeals.remote.itad.models.RemoteFilterIntRange
import pm.bam.gamedeals.remote.itad.models.RemoteItadDealsFilter
import pm.bam.gamedeals.remote.itad.models.RemoteItadDealsRequest
import pm.bam.gamedeals.remote.itad.models.RemoteItadPrice
import pm.bam.gamedeals.remote.itad.models.toGameArtwork
import pm.bam.gamedeals.remote.logic.log
import pm.bam.gamedeals.remote.logic.mapAnyFailure

/**
 * The Winter Sale hub's shelves from `/deals/v2`, always sorted by ITAD popularity (`rank`) and limited to full
 * games. `sort` only works in the POST body: in the query string ITAD ignores it and returns obscure titles first.
 * ITAD has no publisher or tag filter (both are silently ignored), which is why the hub groups by store instead.
 */
internal class ItadWinterSaleHubSource(
    private val dealsApi: ItadDealsApi,
    private val shopsApi: ItadShopsApi,
    private val logger: Logger,
    private val exceptionTransformer: RemoteExceptionTransformer,
) : WinterSaleHubSource {

    /** ITAD's own count per shop. The counts barely differ by country, so treat them as worldwide. */
    override suspend fun shopCounts(country: String): List<WinterSaleShopCount> =
        shopsApi.getShops(country)
            .log(logger, tag = TAG)
            .mapAnyFailure { exceptionTransformer.transformApiException(this) }
            .getOrThrow()
            .map { WinterSaleShopCount(it.id, it.title, it.deals ?: 0) }

    override suspend fun deals(shelf: WinterSaleShelf, country: String, includeAdult: Boolean, offset: Int, limit: Int): WinterSaleDealPage {
        val response = dealsApi.getDeals(
            RemoteItadDealsRequest(
                country = country,
                offset = offset.takeIf { it > 0 },
                limit = limit,
                sort = "rank",
                mature = includeAdult,
                shops = (shelf as? WinterSaleShelf.Store)?.let { listOf(it.shopId) },
                filter = RemoteItadDealsFilter(
                    type = listOf(GAME_TYPE),
                    flag = if (shelf == WinterSaleShelf.AllTimeLows) "H" else null,
                    cut = if (shelf == WinterSaleShelf.BigCuts) RemoteFilterIntRange(WINTER_SALE_BIG_CUT_PERCENT, null) else null,
                ),
            ),
        )
            .log(logger, tag = TAG)
            .mapAnyFailure { exceptionTransformer.transformApiException(this) }
            .getOrThrow()

        val deals = response.list.map { game ->
            WinterSaleDeal(
                gameId = game.id,
                title = game.title,
                artwork = game.assets.toGameArtwork(),
                shopId = game.deal.shop.id,
                shopName = game.deal.shop.name,
                price = game.deal.price.toMoney(),
                regular = game.deal.regular?.toMoney(),
                cutPercent = game.deal.cut,
                url = game.deal.url,
                voucher = game.deal.voucher,
                flag = itadFlag(game.deal.flag),
                mature = game.mature == true,
            )
        }
        return WinterSaleDealPage(deals, response.nextOffset.takeIf { response.hasMore })
    }

    private companion object {
        /** ITAD's product type for full games (DLC, soundtracks and packages are other types). */
        const val GAME_TYPE = 1
        const val TAG = "WinterSaleHub"
    }
}

/** `N` new historical low, `H` at the historical low, `S` the store's own lowest; anything else carries no badge. */
internal fun itadFlag(flag: String?): WinterSaleDealFlag? = when (flag) {
    "N" -> WinterSaleDealFlag.NewLow
    "H" -> WinterSaleDealFlag.AllTimeLow
    "S" -> WinterSaleDealFlag.StoreLow
    else -> null
}

private fun RemoteItadPrice.toMoney() = WinterSaleMoney(amount = amount, currency = currency)
