package pm.bam.gamedeals.domain.models

import androidx.compose.runtime.Immutable

/**
 * A game the user has a personal note on, enriched with the game's [title] and [boxart] for the
 * "My notes" sub-screen (epic #272, P4.2 #283). The ITAD notes endpoint carries only `gameId` + `note`,
 * so the repository enriches each entry via a lightweight `/games/info` lookup; [title] falls back to the
 * raw [gameId] when that lookup fails.
 */
@Immutable
data class NotedGame(
    val gameId: String,
    val note: String,
    val title: String,
    val boxart: String? = null,
)
