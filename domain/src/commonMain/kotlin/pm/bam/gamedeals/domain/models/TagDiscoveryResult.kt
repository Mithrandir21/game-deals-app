package pm.bam.gamedeals.domain.models

import androidx.compose.runtime.Immutable

/**
 * One game in a tag-discovery result page (epic #307). Sourced from IGDB (which is the only catalogue
 * that can be queried by tag), then enriched with an ITAD price where the game is tracked there. The
 * [pricing] discriminator captures the three ways a row can resolve, which the UI uses to decide what
 * happens on tap:
 *
 * - [Pricing.Priced]    — resolved on ITAD → open the in-app Game Page (price may still be null when
 *                          there's no *current* deal; the Game Page shows the historical low).
 * - [Pricing.SteamLinkOut] — has a Steam app id but isn't tracked on ITAD → open the Steam store page.
 * - [Pricing.Unpriced]  — no Steam app id at all (console/mobile-only) → show, but nothing to open.
 */
@Immutable
data class TagDiscoveryResult(
    val igdbId: Long,
    val title: String,
    val coverImageUrl: String?,
    val steamAppId: Int?,
    val pricing: Pricing,
) {
    sealed interface Pricing {
        data class Priced(val gameId: String, val price: BundleGamePrice?) : Pricing
        data class SteamLinkOut(val steamUrl: String) : Pricing
        data object Unpriced : Pricing
    }
}
