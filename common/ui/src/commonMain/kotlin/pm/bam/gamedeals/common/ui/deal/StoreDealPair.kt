package pm.bam.gamedeals.common.ui.deal

import androidx.compose.runtime.Immutable
import pm.bam.gamedeals.domain.models.GameDetails
import pm.bam.gamedeals.domain.models.Store

/**
 * Compose-stable wrapper around a (Store, GameDeal) tuple for the game-centric peek sheet
 * ([GamePeekSheetData]). Replaces `kotlin.Pair<Store, GameDetails.GameDeal>` so wrapping list types
 * (e.g. `ImmutableList<StoreDealPair>`) are considered stable by the Compose compiler.
 *
 * Mirrors `feature.game.ui.StoreDealPair` deliberately — defined here so the shared `:common:ui`
 * sheet doesn't take a dependency on the `:feature:game` module.
 */
@Immutable
data class StoreDealPair(
    val store: Store,
    val deal: GameDetails.GameDeal,
)
