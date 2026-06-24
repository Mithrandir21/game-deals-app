@file:UseSerializers(ImmutableListSerializer::class)

package pm.bam.gamedeals.domain.models


import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import pm.bam.gamedeals.domain.utils.ImmutableListSerializer

/**
 * A game bundle from a storefront (epic #205, Phase 3c; redesigned in the Bundles redesign work). Source-
 * neutral: ITAD fills these from `/bundles/v1` (region-aware); a provider without bundles returns none.
 *
 * Serialized to a region-keyed blob in `BundlesCache` (ITAD caching strategy, Phase 5b, #266) — the
 * whole `List<Bundle>` for a region is one cache row (feed tier, 12h), and the detail screen re-resolves
 * a [Bundle] by [id] from that cached list. The `@file:UseSerializers` makes the nested [ImmutableList]
 * fields round-trip through kotlinx-serialization.
 *
 * The detail screen renders the bundle by [tiers] (each tier's price + its games); the compact list row
 * uses the flattened [games] (union across tiers, for the cover-art strip) plus [priceDenominated] (the
 * cheapest tier). All fields the redesign added carry defaults so an older cached blob still decodes.
 *
 * [url] is the store's affiliate link (opened to claim the bundle — ITAD's ToS requires the link be kept
 * intact). [details] is an optional store-supplied blurb; [isMature] gates the bundle behind the mature
 * opt-in setting; [publishEpochMs] backs the "Newest" sort and the published-date footer.
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
    val publishEpochMs: Long? = null,
    val isMature: Boolean = false,
    val details: String? = null,
    val priceValue: Double? = null,
    val tiers: ImmutableList<Tier> = persistentListOf(),
) {
    @Immutable
    @Serializable
    data class Tier(
        val priceDenominated: String?,
        val priceValue: Double? = null,
        val games: ImmutableList<BundleGame>,
    )

    @Immutable
    @Serializable
    data class BundleGame(
        val id: String,
        val title: String,
        val artwork: GameArtwork = GameArtwork(),
    )
}
