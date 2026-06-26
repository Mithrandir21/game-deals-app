package pm.bam.gamedeals.domain.models

import androidx.compose.runtime.Immutable

/**
 * A game in the local recently-viewed history (#211). [gameId] is the ITAD id (the app-wide key used to
 * re-open the game/peek sheet); [boxart] is the cover url shown on the carousel tile, null when unknown.
 */
@Immutable
data class RecentlyViewedGame(
    val gameId: String,
    val title: String,
    val boxart: String?,
)
