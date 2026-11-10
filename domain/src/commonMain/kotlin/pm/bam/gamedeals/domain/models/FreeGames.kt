package pm.bam.gamedeals.domain.models

/**
 * A price of nothing with no discount: the game is simply free (free-to-play, or given away for good). ITAD still
 * flags it as at its all-time low, but there is no deal to point at, so it never shows the "H" letter. A 100% cut
 * (free for a while) and a game that has just gone free (ITAD's "N") are real news and keep theirs.
 */
fun isFreeWithoutDiscount(price: Double?, cutPercent: Int?): Boolean = price == 0.0 && (cutPercent ?: 0) <= 0
