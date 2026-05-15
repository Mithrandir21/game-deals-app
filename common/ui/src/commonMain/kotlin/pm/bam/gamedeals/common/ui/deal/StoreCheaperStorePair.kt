package pm.bam.gamedeals.common.ui.deal

import androidx.compose.runtime.Immutable
import pm.bam.gamedeals.domain.models.DealDetails
import pm.bam.gamedeals.domain.models.Store

/**
 * Compose-stable wrapper around a (Store, CheaperStore) tuple.
 *
 * Replaces `kotlin.Pair<Store, DealDetails.CheaperStore>` in composable
 * parameter types so that wrapping list types are considered stable by the
 * Compose compiler and skipping can apply.
 */
@Immutable
data class StoreCheaperStorePair(
    val store: Store,
    val cheaperStore: DealDetails.CheaperStore,
)
