package pm.bam.gamedeals.domain.models

/**
 * Why a deal is a rare discount: nothing in the year before this sale was as deep.
 *
 * [lastThisDeepEpochMs] is the end of the most recent week, before that year, with a discount at least as deep, or
 * null when the whole history the Worker keeps (two years) has nothing as deep ("deepest in 2 years").
 */
data class RareDiscount(val lastThisDeepEpochMs: Long?)

/** The smallest discount that can count as rare; a "rare" 10% off helps nobody. */
const val RARE_DISCOUNT_MIN_CUT = 20

private const val YEAR_WEEKS = 52
private const val WEEK_MS = 7L * 24 * 60 * 60 * 1000

/**
 * Longest a sale can have run and still count as "this sale". A discount this deep that has lasted longer is the
 * game's usual price, not a rare one.
 */
private const val MAX_SALE_WEEKS = 6

/**
 * Whether a deal at [currentCut] percent off is rare for this game: at least [RARE_DISCOUNT_MIN_CUT]% off, and no
 * week in the 52 before this sale had a discount as deep. Null when it isn't rare, or when there's too little history
 * to tell.
 *
 * The weeks of this sale itself (the trailing weeks already at least this deep, up to [MAX_SALE_WEEKS]) are skipped,
 * so a sale doesn't disqualify itself. The Worker's weeks can be up to a day behind; a sale that started since then
 * simply has no trailing weeks yet.
 */
fun GameSignals.rareDiscount(currentCut: Int): RareDiscount? {
    if (currentCut < RARE_DISCOUNT_MIN_CUT) return null
    val weeks = weeklyMaxCut
    if (weeks.size <= YEAR_WEEKS) return null
    var saleStart = weeks.size
    while (saleStart > 0 && weeks[saleStart - 1] >= currentCut) saleStart--
    if (weeks.size - saleStart > MAX_SALE_WEEKS) return null
    if (saleStart < YEAR_WEEKS) return null
    val yearBefore = weeks.subList(saleStart - YEAR_WEEKS, saleStart)
    if (yearBefore.any { it >= currentCut }) return null
    val lastDeep = (saleStart - YEAR_WEEKS - 1 downTo 0).firstOrNull { weeks[it] >= currentCut }
    // Week i ends (size - 1 - i) weeks before the last week's end, which is when the Worker fetched the history.
    return RareDiscount(lastThisDeepEpochMs = lastDeep?.let { updatedEpochMs - (weeks.size - 1 - it) * WEEK_MS })
}
