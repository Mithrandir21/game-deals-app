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
    /** Steam's player tags, most votes first (via ITAD); see [specificTags]. */
    val tags: List<String> = emptyList(),
    val developer: String? = null,
    val releaseYear: Int? = null,
    val steamAppId: Int? = null,
) {
    /**
     * Up to [count] tags that say what the game is. Tags true of most games (Indie, Singleplayer, Great Soundtrack)
     * are left out; very broad genres (Action, Adventure) only fill in when too few specific ones are left.
     */
    fun specificTags(count: Int = 3): List<String> {
        val kept = tags.filterNot { it.lowercase() in VAGUE_TAGS }
        val (broad, specific) = kept.partition { it.lowercase() in BROAD_TAGS }
        return (specific + broad).take(count)
    }
}

/** Steam tags that fit thousands of games, so they say nothing about this one. Lower case. */
private val VAGUE_TAGS = setOf(
    "indie", "singleplayer", "multiplayer", "great soundtrack", "atmospheric", "beautiful", "colorful", "cute",
    "funny", "classic", "cult classic", "masterpiece", "addictive", "family friendly", "replay value", "controller",
    "free to play", "early access",
)

/** Tags so broad (genres, viewpoints) that they only fill in when a game has too few specific ones. Lower case. */
private val BROAD_TAGS = setOf(
    "action", "adventure", "simulation", "strategy", "rpg", "casual", "story rich", "2d", "3d", "first-person",
    "third person",
)

/** One answer from the Worker: the games it has, and the ones it is still fetching (ask again later). */
data class GameSignalsBatch(
    val games: List<GameSignals>,
    val pending: List<String>,
)
