package pm.bam.gamedeals.feature.game.ui

import androidx.compose.runtime.Immutable
import pm.bam.gamedeals.domain.models.DealQuality

/**
 * The single source of price truth for the game page hero (and its condensed sticky bar): the cheapest
 * current deal paired with its store, plus the [DealQuality] buy-signal (how that price compares to the
 * all-time low). Derived purely from [GamePageViewModel.GamePageData.Data] — no extra fetch — so the hero
 * and the sticky bar can never disagree about "the best price".
 */
@Immutable
data class BuyBoxState(
    val pair: StoreDealPair,
    val quality: DealQuality?,
)
