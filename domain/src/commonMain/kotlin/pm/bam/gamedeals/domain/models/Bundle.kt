package pm.bam.gamedeals.domain.models


import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList

/**
 * A game bundle from a storefront (epic #205, Phase 3c). Source-neutral: ITAD fills these from
 * `/bundles/v1` (region-aware); a provider without bundles returns none. Not a Room entity — fetched
 * on demand (the nested tier/game structure makes caching costly), so the detail screen re-resolves a
 * [Bundle] by [id] from the same source.
 *
 * [priceDenominated] is the cheapest tier's price; [url] is the store's affiliate link (opened to claim
 * the bundle — ITAD's ToS requires the link be kept intact).
 */
@Immutable
data class Bundle(
    val id: Int,
    val title: String,
    val storeName: String,
    val url: String,
    val expiryEpochMs: Long?,
    val gameCount: Int,
    val priceDenominated: String?,
    val games: ImmutableList<BundleGame>,
) {
    @Immutable
    data class BundleGame(
        val id: String,
        val title: String,
        val boxart: String,
    )
}
