package pm.bam.gamedeals.domain.models

import androidx.compose.runtime.Immutable

/** A user's personal note on a game (epic #272, P4 — from `/user/notes/v1`). */
@Immutable
data class ItadNote(
    val gameId: String,
    val note: String,
)
