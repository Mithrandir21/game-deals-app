package pm.bam.gamedeals.feature.game.ui

import androidx.compose.runtime.Immutable
import pm.bam.gamedeals.domain.models.GameDetails
import pm.bam.gamedeals.domain.models.Store

/**
 * Compose-stable wrapper around a (Store, GameDeal) tuple.
 *
 * Replaces `kotlin.Pair<Store, GameDetails.GameDeal>` in composable parameter
 * types so that wrapping list types (e.g. `ImmutableList<StoreDealPair>`) are
 * considered stable by the Compose compiler and skipping can apply.
 */
@Immutable
data class StoreDealPair(
    val store: Store,
    val deal: GameDetails.GameDeal,
)
