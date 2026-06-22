package pm.bam.gamedeals.domain.models

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * User-chosen server-side filters for the Deals tab, applied via the `filter` object of the
 * `POST /deals/v2` request body (epic follow-up — ITAD's rich deal filters, confirmed available on the
 * public API via POST rather than the GET query string). All set dimensions AND-combine.
 *
 * `null`/empty fields mean "no constraint on that dimension". The values are deliberately kept at the
 * product level (thresholds, enums) — the ITAD wire encoding (range objects, numeric ids, the current
 * year for release windows) is applied by the remote layer's request mapper.
 *
 * Persisted across launches via [pm.bam.gamedeals.domain.repositories.settings.SettingsRepository]
 * (Deals-only; not shared with the Bundles tab).
 */
@Immutable
@Serializable
data class DealsFilter(
    /** Minimum discount percent (e.g. 50 = "50%+ off"); maps to ITAD `cut {min,max:100}`. */
    val minCutPercent: Int? = null,
    /** Maximum sale price in the region's currency (0.0 = Free); maps to ITAD `price {min:null,max}`. */
    val maxPrice: Double? = null,
    /** Product types to include (empty = all); maps to ITAD `type [..]`. */
    val types: Set<ProductType> = emptySet(),
    /** When true, restrict to DRM-free titles; maps to ITAD `drm [1000]`. */
    val drmFree: Boolean = false,
    /** Restrict to a deal flag (new/historical/shop low); maps to ITAD `flag`. */
    val flag: DealFlag? = null,
    /** Minimum Steam review percent (e.g. 80 = "80%+"); maps to ITAD `steamPerc {min,max:100}`. */
    val minSteamPercent: Int? = null,
    /** Release-recency window; maps to ITAD `releaseDays` / `releaseYear`. */
    val release: ReleaseWindow? = null,
) {
    /** Whether no filter dimension is constrained (used to omit the `filter` object entirely). */
    fun isEmpty(): Boolean = activeCount == 0

    /** Number of constrained dimensions — drives the filter-bar's active-count badge. */
    val activeCount: Int
        get() = listOf(
            minCutPercent != null,
            maxPrice != null,
            types.isNotEmpty(),
            drmFree,
            flag != null,
            minSteamPercent != null,
            release != null,
        ).count { it }
}

/*
 * PERSISTED CONTRACT — these three enums are serialized **by Kotlin constant name** (no @SerialName)
 * inside the `deals_filter` Storage blob. Renaming a constant silently orphans a user's saved filter
 * after launch. Add new constants freely; never rename or remove an existing one without a read-time
 * migration. (`apiValue` is the ITAD wire encoding and is safe to change — it isn't persisted.)
 */

/** ITAD product type (`type` filter); [apiValue] is the numeric id ITAD expects. */
enum class ProductType(val apiValue: Int) {
    Game(1),
    Dlc(2),
    Bundle(3),
}

/** ITAD deal flag (`flag` filter). The server treats these hierarchically: [ShopLow] ⊃ [HistoricalLow] ⊃ [NewLow]. */
enum class DealFlag(val apiValue: String) {
    /** New all-time historical low (`N`). */
    NewLow("N"),
    /** Historical low (`H`). */
    HistoricalLow("H"),
    /** Shop low (`S`). */
    ShopLow("S"),
}

/** Release-recency window for the Deals filter. The remote mapper resolves these against the current year. */
enum class ReleaseWindow {
    /** Released in the last 90 days (`releaseDays:90`). */
    NewLast90,
    /** Released this calendar year (`releaseYear {min:Y,max:Y}`). */
    ThisYear,
    /** Released more than two years ago (`releaseYear {min:null,max:Y-2}`). */
    TwoPlusYears,
}
