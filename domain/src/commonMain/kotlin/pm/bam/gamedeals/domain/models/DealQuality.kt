package pm.bam.gamedeals.domain.models

import androidx.compose.runtime.Immutable
import kotlin.math.roundToInt

/**
 * A buy-signal derived **purely** from a [GameDetails]: how the current best live price compares to the
 * game's all-time historical low (ITAD `cheapestPriceEver`). This lets the game page tell a deal-hunter
 * "this is the lowest it's ever been" vs "it was cheaper before" without any extra API call — the
 * all-time low is already on every game-details payload.
 */
@Immutable
data class DealQuality(
    val tier: Tier,
    /** Whole-percent the current best price sits above the all-time low; 0 when at or below the low. */
    val percentAboveLow: Int,
    val allTimeLowDenominated: String,
) {
    enum class Tier {
        /** Current best price matches (or beats) the all-time low — the strongest buy signal. */
        AllTimeLow,

        /** Within [NEAR_LOW_MAX_PERCENT]% of the all-time low — still a strong deal. */
        NearLow,

        /** Meaningfully above the all-time low — it has been cheaper before. */
        Elevated,
    }

    companion object {
        // Half a cent: a float-safe "the prices are equal" tolerance.
        internal const val PRICE_EPSILON: Double = 0.005

        // Within this many percent of the all-time low still reads as a strong deal.
        internal const val NEAR_LOW_MAX_PERCENT: Int = 10
    }
}

/**
 * Computes the [DealQuality] for a game, or `null` when there's nothing to compare — no live deal, or a
 * non-positive best price (free / unknown). Pure (no IO), so it's JVM-unit-testable.
 */
fun GameDetails.dealQuality(): DealQuality? {
    val bestPrice = deals.minByOrNull { it.priceValue }?.priceValue ?: return null
    if (bestPrice <= 0.0) return null

    // No recorded paid low (0 / unknown) → there's nothing meaningful to compare against.
    val low = cheapestPriceEver.priceValue
    if (low <= 0.0) return null

    val atOrBelowLow = bestPrice <= low + DealQuality.PRICE_EPSILON
    val percentAboveLow = if (atOrBelowLow) 0 else (((bestPrice - low) / low) * 100).roundToInt().coerceAtLeast(0)
    val tier = when {
        atOrBelowLow -> DealQuality.Tier.AllTimeLow
        percentAboveLow <= DealQuality.NEAR_LOW_MAX_PERCENT -> DealQuality.Tier.NearLow
        else -> DealQuality.Tier.Elevated
    }
    return DealQuality(
        tier = tier,
        percentAboveLow = percentAboveLow,
        allTimeLowDenominated = cheapestPriceEver.priceDenominated,
    )
}
