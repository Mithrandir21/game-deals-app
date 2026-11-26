package pm.bam.gamedeals.domain.models

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList

/**
 * A signed-in user's year on ITAD, for the shareable "Year in deals" card. Built from their collection and
 * waitlist (both carry the date each game was added) plus ITAD's all-time lows and current prices.
 * Every section is optional: a section with nothing to say is null and the card leaves it out.
 */
@Immutable
data class YearInDeals(
    val year: Int,
    /** Games added to the collection this year, leaving out days that look like a library import. */
    val collected: CollectedGames?,
    /**
     * What the counted games would cost at their regular price today, formatted in the user's currency. A
     * bragging number, not savings: we don't know what anyone paid.
     */
    val fullPriceValue: String?,
    /** Waitlisted games whose all-time low was set this year. */
    val waitlistWins: WaitlistWins?,
    /** The game that has been on the waitlist the longest. */
    val longestWait: LongestWait?,
) {
    val isEmpty: Boolean get() = collected == null && waitlistWins == null && longestWait == null
}

@Immutable
data class YearGame(val gameId: String, val title: String, val artwork: GameArtwork = GameArtwork())

@Immutable
data class CollectedGames(val count: Int, val highlights: ImmutableList<YearGame>)

@Immutable
data class WaitlistWins(val count: Int, val biggestDrop: BiggestDrop?)

@Immutable
data class BiggestDrop(
    val game: YearGame,
    val cutPercent: Int,
    val lowDenominated: String,
    val regularDenominated: String?,
)

@Immutable
data class LongestWait(val game: YearGame, val days: Int)

/** A game's all-time low (ITAD `/games/historylow/v1`): price, regular price, cut and when it was set. */
@Immutable
data class GameHistoryLow(
    val gameId: String,
    val price: Double,
    val regular: Double?,
    val cutPercent: Int,
    val currency: String,
    val setAtEpochMs: Long?,
)
