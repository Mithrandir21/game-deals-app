package pm.bam.gamedeals.domain.models

import androidx.compose.runtime.Immutable

/**
 * A game's public crowd and sale signals, from the Game Deals Worker's once-a-day copy of ITAD data
 * (`store/site/signals-routes.mjs`). Shared by every user, so rows can show them without each phone calling ITAD.
 */
@Immutable
data class GameSignals(
    val gameId: String,
    /** When the Worker fetched this from ITAD. */
    val updatedEpochMs: Long,
    /**
     * The deepest discount (0–100) any store offered during each of the last weeks, oldest first; the last week ends
     * at [updatedEpochMs]. From the US price history; discounts are almost always the same percentage everywhere.
     */
    val weeklyMaxCut: List<Int>,
    /** Review score out of 100, preferring Steam's; null when ITAD has none. */
    val reviewScore: Int? = null,
    val reviewCount: Int? = null,
    val reviewSource: String? = null,
    /** Players right now and the all-time peak (Steam, via ITAD). */
    val playersRecent: Int? = null,
    val playersPeak: Int? = null,
    /** How many ITAD users waitlist / own the game, and its popularity rank on ITAD. */
    val waitlisted: Int? = null,
    val collected: Int? = null,
    val rank: Int? = null,
)

/** One answer from the Worker: the games it has, and the ones it is still fetching (ask again later). */
data class GameSignalsBatch(
    val games: List<GameSignals>,
    val pending: List<String>,
)
