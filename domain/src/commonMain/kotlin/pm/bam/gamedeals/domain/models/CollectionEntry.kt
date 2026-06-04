package pm.bam.gamedeals.domain.models

import androidx.compose.runtime.Immutable

/** A game in the user's ITAD collection (epic #219, Phase 2 — from `/collection/games/v1`). */
@Immutable
data class CollectionEntry(
    val gameId: String,
    val title: String,
    val boxart: String? = null,
)
