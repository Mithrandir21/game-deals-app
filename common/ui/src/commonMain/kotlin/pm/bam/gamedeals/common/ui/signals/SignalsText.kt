package pm.bam.gamedeals.common.ui.signals

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import pm.bam.gamedeals.common.ui.generated.resources.Res
import pm.bam.gamedeals.common.ui.generated.resources.deal_review_other
import pm.bam.gamedeals.common.ui.generated.resources.deal_review_steam
import pm.bam.gamedeals.domain.models.GameSignals

/** Fewer Steam reviews than this say little, so rows leave the score out. */
internal const val MIN_STEAM_REVIEWS = 50

/** Which review a row shows, if any: Steam's as "% positive", another source by name. Null when there's nothing to show. */
internal fun GameSignals.rowReview(): RowReview? {
    val score = reviewScore ?: return null
    val source = reviewSource ?: return null
    return if (source == "Steam") {
        RowReview.SteamPositive(score).takeIf { (reviewCount ?: MIN_STEAM_REVIEWS) >= MIN_STEAM_REVIEWS }
    } else {
        RowReview.Scored(source, score)
    }
}

internal sealed interface RowReview {
    data class SteamPositive(val percent: Int) : RowReview
    data class Scored(val source: String, val score: Int) : RowReview
}

/** The row's review line, e.g. "97% positive" or "Metascore 88"; null when the game has no review worth showing. */
@Composable
fun GameSignals.reviewText(): String? = when (val review = rowReview()) {
    is RowReview.SteamPositive -> stringResource(Res.string.deal_review_steam, review.percent)
    is RowReview.Scored -> stringResource(Res.string.deal_review_other, review.source, review.score)
    null -> null
}

/** [gameId]'s review line from these signals, if any. */
@Composable
fun Map<String, GameSignals>.reviewText(gameId: String?): String? = gameId?.let { this[it] }?.reviewText()

/** A count made short for a glance: 950, 1.2k, 45k, 1.2M. */
fun formatCompactCount(value: Int): String = when {
    value < 1_000 -> value.toString()
    value < 10_000 -> oneDecimal(value / 100, "k")
    value < 1_000_000 -> "${value / 1_000}k"
    value < 10_000_000 -> oneDecimal(value / 100_000, "M")
    else -> "${value / 1_000_000}M"
}

/** [tenths] / 10 with one decimal, dropping ".0" (12 → "1.2k", 10 → "1k"). */
private fun oneDecimal(tenths: Int, unit: String): String =
    if (tenths % 10 == 0) "${tenths / 10}$unit" else "${tenths / 10}.${tenths % 10}$unit"
