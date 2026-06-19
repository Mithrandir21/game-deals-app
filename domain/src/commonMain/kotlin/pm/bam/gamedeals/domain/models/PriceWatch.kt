package pm.bam.gamedeals.domain.models

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * A user-set **target-price alert** for a game (Phase 3). Unlike the ITAD waitlist — which notifies on
 * *any* deal — a price watch fires only once the game's current best price falls **at or below**
 * [targetPriceValue]. Purely client-side (ITAD has no per-user threshold API), so it's persisted locally
 * via [Storage][pm.bam.gamedeals.common.storage.Storage] like the other user preferences.
 *
 * [targetPriceValue] is the comparison input (region currency); [targetPriceDenominated] is the display
 * string. [lastNotifiedPriceValue] dedupes the background check: it records the price at the last alert so
 * a still-low price doesn't re-notify every poll, and is cleared when the price rises back above target so
 * a future drop alerts again.
 */
@Immutable
@Serializable
data class PriceWatch(
    val gameId: String,
    val title: String,
    val targetPriceValue: Double,
    val targetPriceDenominated: String,
    val country: String,
    val createdAtMs: Long,
    val lastNotifiedPriceValue: Double? = null,
)
