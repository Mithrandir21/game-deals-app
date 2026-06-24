package pm.bam.gamedeals.common.ui.deal

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import pm.bam.gamedeals.domain.models.GameDetails

/**
 * State for the game-centric peek sheet ([GamePeekSheet]) — the single quick-peek surface opened from
 * every game/deal row across Home and Deals (row-consolidation work). Unlike the retired deal-centric
 * sheet, it is keyed by [gameId] (not a deal id + store) and loaded via
 * [GamesRepository.getGameDetails][pm.bam.gamedeals.domain.repositories.games.GamesRepository.getGameDetails],
 * so the same sheet works for a trending deal, a ranked game, or an unreleased "new release".
 *
 * [thumb], [gameName] and [gameId] are carried on every state so the header renders immediately while
 * the deals load. A [Data] with `bestDeal == null` (`upcoming == true`) is the "no deals yet" state
 * shown for unreleased releases; its [gameId] may be blank when a title never resolved to an ITAD game,
 * in which case "View game page" falls back to a title lookup (handled by the caller).
 */
@Immutable
sealed class GamePeekSheetData {
    abstract val gameId: String
    abstract val gameName: String
    abstract val thumb: String?

    @Immutable
    data class Loading(
        override val gameId: String,
        override val gameName: String,
        override val thumb: String? = null,
    ) : GamePeekSheetData()

    @Immutable
    data class Data(
        override val gameId: String,
        override val gameName: String,
        override val thumb: String?,
        /** The cheapest current deal across stores, or null when the game has no deal (upcoming). */
        val bestDeal: StoreDealPair?,
        /** Up to [GamePeekController.MAX_OTHER_STORES] more stores, sorted by price (excludes [bestDeal]). */
        val otherStores: ImmutableList<StoreDealPair> = persistentListOf(),
        val cheapestPriceEver: GameDetails.GameCheapestPriceEver? = null,
        /** True when there is no current deal (an unreleased / not-yet-sold game). */
        val upcoming: Boolean = false,
    ) : GamePeekSheetData()

    @Immutable
    data class Error(
        override val gameId: String,
        override val gameName: String,
        override val thumb: String? = null,
    ) : GamePeekSheetData()
}
