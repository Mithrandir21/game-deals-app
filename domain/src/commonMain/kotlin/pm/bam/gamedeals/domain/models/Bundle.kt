@file:UseSerializers(ImmutableListSerializer::class)

package pm.bam.gamedeals.domain.models


import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import pm.bam.gamedeals.domain.utils.ImmutableListSerializer

/**
 * A game bundle from a storefront (epic #205, Phase 3c). Source-neutral: ITAD fills these from
 * `/bundles/v1` (region-aware); a provider without bundles returns none.
 *
 * Serialized to a region-keyed blob in `BundlesCache` (ITAD caching strategy, Phase 5b, #266) — the
 * whole `List<Bundle>` for a region is one cache row (feed tier, 12h), and the detail screen re-resolves
 * a [Bundle] by [id] from that cached list. The `@file:UseSerializers` makes the nested [ImmutableList]
 * field round-trip through kotlinx-serialization.
 *
 * [priceDenominated] is the cheapest tier's price; [url] is the store's affiliate link (opened to claim
 * the bundle — ITAD's ToS requires the link be kept intact).
 */
@Immutable
@Serializable
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
    @Serializable
    data class BundleGame(
        val id: String,
        val title: String,
        val boxart: String,
    )
}
