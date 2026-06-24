package pm.bam.gamedeals.common.ui.share

/**
 * Builds the human-readable text that platform share sheets carry for a deal.
 * Behind an interface so ViewModel unit tests can mock it and assert call args
 * without coupling to the exact formatting.
 */
interface DealShareTextBuilder {
    fun build(
        gameTitle: String,
        salePriceDenominated: String,
        storeName: String,
        dealUrl: String,
    ): String
}

internal class DefaultDealShareTextBuilder : DealShareTextBuilder {
    override fun build(
        gameTitle: String,
        salePriceDenominated: String,
        storeName: String,
        dealUrl: String,
    ): String = "$gameTitle — $salePriceDenominated at $storeName: $dealUrl"
}
