package pm.bam.gamedeals.domain.models

import androidx.compose.runtime.Immutable

/** A game on the user's ITAD waitlist (epic #219, Phase 2 — from `/waitlist/games/v1`). */
@Immutable
data class WaitlistEntry(
    val gameId: String,
    val title: String,
    val boxart: String? = null,
)
