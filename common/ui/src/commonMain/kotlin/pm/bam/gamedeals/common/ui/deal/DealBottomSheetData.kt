package pm.bam.gamedeals.common.ui.deal

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import pm.bam.gamedeals.domain.models.DealDetails
import pm.bam.gamedeals.domain.models.Store

@Immutable
sealed class DealBottomSheetData(
    open val store: Store,
    open val gameId: Int,
    open val gameName: String,
    open val dealId: String,
    open val dealUrl: String,
    open val gameSalesPriceDenominated: String,
) {
    @Immutable
    data class DealDetailsData(
        override val store: Store,
        override val gameId: Int,
        override val gameName: String,
        override val dealId: String,
        override val gameSalesPriceDenominated: String,
        val gameInfo: DealDetails.GameInfo,
        val cheaperStores: ImmutableList<StoreCheaperStorePair>,
        val cheapestPrice: DealDetails.CheapestPrice?,
        override val dealUrl: String = "",
    ) : DealBottomSheetData(store, gameId, gameName, dealId, dealUrl, gameSalesPriceDenominated)

    @Immutable
    data class DealDetailsLoading(
        override val store: Store,
        override val gameId: Int,
        override val gameName: String,
        override val dealId: String,
        override val gameSalesPriceDenominated: String,
        override val dealUrl: String = "",
    ) : DealBottomSheetData(store, gameId, gameName, dealId, dealUrl, gameSalesPriceDenominated)

    @Immutable
    data class DealDetailsError(
        override val store: Store,
        override val gameId: Int,
        override val gameName: String,
        override val dealId: String,
        override val gameSalesPriceDenominated: String,
        override val dealUrl: String = "",
    ) : DealBottomSheetData(store, gameId, gameName, dealId, dealUrl, gameSalesPriceDenominated)
}
