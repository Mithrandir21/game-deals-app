package pm.bam.gamedeals.domain.models

private const val CHEAPSHARK_DEAL_REDIRECT_PREFIX = "https://www.cheapshark.com/redirect?dealID="

fun cheapsharkDealRedirectUrl(dealId: String): String =
    CHEAPSHARK_DEAL_REDIRECT_PREFIX + dealId
