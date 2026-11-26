package pm.bam.gamedeals.domain.models

import androidx.compose.runtime.Immutable

/**
 * A game referenced by a waitlist notification (epic #272, P2 follow-up #288 — from
 * `/notifications/waitlist/v1`). Only the minimal subset needed to deep-link is modelled: the ITAD
 * game [gameId] (the same id [Destination.Game][pm.bam.gamedeals.common.navigation.Destination.Game]
 * navigates with) and its [title] for the multi-game chooser.
 */
@Immutable
data class NotificationGame(
    val gameId: String,
    val title: String,
)
