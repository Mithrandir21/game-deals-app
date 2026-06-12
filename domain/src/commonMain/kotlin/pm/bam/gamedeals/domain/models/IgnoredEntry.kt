package pm.bam.gamedeals.domain.models

import androidx.compose.runtime.Immutable

/** A game on the user's ITAD ignore list (epic #272, P3 — from `/ignored/games/v1`). */
@Immutable
data class IgnoredEntry(
    val gameId: String,
    val title: String,
    val boxart: String? = null,
)
