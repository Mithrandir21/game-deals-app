package pm.bam.gamedeals.remote.itad

import com.skydoves.sandwich.getOrThrow
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleMoney
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleOffer
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleOfferSource
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.remote.exceptions.RemoteExceptionTransformer
import pm.bam.gamedeals.remote.itad.api.ItadGamesApi
import pm.bam.gamedeals.remote.itad.models.RemoteItadOverviewDrm
import pm.bam.gamedeals.remote.itad.models.RemoteItadPrice
import pm.bam.gamedeals.remote.logic.log
import pm.bam.gamedeals.remote.logic.mapAnyFailure

internal class ItadWinterSaleOfferSource(
    private val gamesApi: ItadGamesApi,
    private val logger: Logger,
    private val exceptionTransformer: RemoteExceptionTransformer,
) : WinterSaleOfferSource {
    override suspend fun fetch(gameIds: List<String>, country: String): Map<String, WinterSaleOffer?> {
        require(gameIds.size in 1..MAX_IDS_PER_REQUEST)
        val response = gamesApi.getOverview(gameIds, country)
            .log(logger, tag = TAG)
            .mapAnyFailure { exceptionTransformer.transformApiException(this) }
            .getOrThrow()

        return response.prices.associate { game ->
            val current = game.current
            game.id to if (current == null) null else WinterSaleOffer(
                gameId = game.id,
                country = country,
                shopId = current.shop.id,
                shopName = current.shop.name,
                price = current.price.toWinterSaleMoney(),
                regular = current.regular?.toWinterSaleMoney(),
                cutPercent = current.cut,
                url = current.url,
                voucher = current.voucher,
                activationLabels = current.drm.mapNotNull(RemoteItadOverviewDrm::knownLabel).distinct(),
                checkedAtEpochMillis = current.timestamp.toEpochMillisOrNull(),
                expiresAtEpochMillis = current.expiry.toEpochMillisOrNull(),
                historicalLow = game.lowest?.price?.toWinterSaleMoney(),
            )
        }
    }

    private companion object {
        const val MAX_IDS_PER_REQUEST = 200
        const val TAG = "WinterSaleOffers"
    }
}

private fun RemoteItadPrice.toWinterSaleMoney() = WinterSaleMoney(amount = amount, currency = currency)

private fun RemoteItadOverviewDrm.knownLabel(): String? = when (id) {
    16 -> "Epic"
    35 -> "GOG"
    48 -> "Microsoft Store"
    61 -> "Steam"
    62 -> "Ubisoft Store"
    501 -> "Rockstar"
    1000 -> "DRM-free"
    else -> null
}

@OptIn(ExperimentalTime::class)
private fun String?.toEpochMillisOrNull(): Long? = this?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() }
