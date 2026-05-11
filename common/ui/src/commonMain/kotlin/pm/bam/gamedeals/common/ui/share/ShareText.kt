package pm.bam.gamedeals.common.ui.share

import pm.bam.gamedeals.domain.models.cheapsharkDealRedirectUrl

fun buildDealShareText(
    gameTitle: String,
    salePriceDenominated: String,
    storeName: String,
    dealId: String,
): String = "$gameTitle — $salePriceDenominated at $storeName: ${cheapsharkDealRedirectUrl(dealId)}"
