package pm.bam.gamedeals.domain.models

import androidx.compose.runtime.Immutable

/**
 * A game's cheapest current price in one storefront region (epic #291, Phase 7 — the Game Page's
 * "Regions" tab). Built by querying ITAD `/games/prices/v3` with a `country=` per region and keeping the
 * cheapest deal. [priceValue] is in that region's currency (already reflected in [priceDenominated]); raw
 * values are **not** comparable across currencies — the tab presents them side by side, it doesn't convert.
 */
@Immutable
data class RegionalPrice(
    val country: Country,
    val priceValue: Double,
    val priceDenominated: String,
    val url: String,
)
