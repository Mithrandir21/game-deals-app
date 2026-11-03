package pm.bam.gamedeals.domain.repositories.wintersale

import kotlin.coroutines.cancellation.CancellationException
import pm.bam.gamedeals.common.time.Clock

data class WinterSaleMoney(val amount: Double, val currency: String)

data class WinterSaleOffer(
    val gameId: String,
    val country: String,
    val shopId: Int,
    val shopName: String,
    val price: WinterSaleMoney,
    val regular: WinterSaleMoney?,
    val cutPercent: Int,
    val url: String,
    val voucher: String?,
    val activationLabels: List<String>,
    val checkedAtEpochMillis: Long?,
    val expiresAtEpochMillis: Long?,
    val historicalLow: WinterSaleMoney?,
) {
    val isDiscounted: Boolean get() = cutPercent > 0
    val isAtHistoricalLow: Boolean get() =
        historicalLow?.takeIf { it.currency == price.currency }?.let { price.amount <= it.amount } ?: false
}

interface WinterSaleOfferSource {
    suspend fun fetch(gameIds: List<String>, country: String): Map<String, WinterSaleOffer?>
}

sealed interface WinterSalePriceStatus {
    data class Priced(val offer: WinterSaleOffer) : WinterSalePriceStatus
    data object NoCurrentOffer : WinterSalePriceStatus
    data object Unavailable : WinterSalePriceStatus
}

data class WinterSaleOfferLoad(
    val country: String,
    val refreshedAtEpochMillis: Long,
    val perGame: Map<String, WinterSalePriceStatus>,
)

interface WinterSaleOffersRepository {
    suspend fun load(gameIds: List<String>, country: String): WinterSaleOfferLoad
}

internal class WinterSaleOffersRepositoryImpl(
    private val source: WinterSaleOfferSource,
    private val clock: Clock,
) : WinterSaleOffersRepository {
    override suspend fun load(gameIds: List<String>, country: String): WinterSaleOfferLoad {
        require(country.isNotBlank())
        val ids = gameIds.filter { it.isNotBlank() }.distinct()
        val statuses = linkedMapOf<String, WinterSalePriceStatus>()
        for (chunk in ids.chunked(MAX_IDS_PER_REQUEST)) {
            val fetched = try {
                source.fetch(chunk, country)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                emptyMap()
            }
            for (id in chunk) {
                val offer = fetched[id]
                statuses[id] = when {
                    id !in fetched -> WinterSalePriceStatus.Unavailable
                    offer == null -> WinterSalePriceStatus.NoCurrentOffer
                    offer.country != country || offer.gameId != id -> WinterSalePriceStatus.Unavailable
                    else -> WinterSalePriceStatus.Priced(offer)
                }
            }
        }
        return WinterSaleOfferLoad(country, clock.nowMillis(), statuses)
    }

    private companion object {
        const val MAX_IDS_PER_REQUEST = 200
    }
}
