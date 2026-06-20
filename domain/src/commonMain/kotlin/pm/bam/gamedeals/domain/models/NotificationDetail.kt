package pm.bam.gamedeals.domain.models

import androidx.compose.runtime.Immutable

/**
 * The full deal content of one waitlist notification (from `/notifications/waitlist/v1`), mirroring the
 * ITAD website's "deals inside a daily notification" view. Built for the in-app notification **detail**
 * screen: a notification is a daily digest spanning several waitlisted [games], each carrying its current
 * shop [NotificationDealGame.deals].
 *
 * The detail payload carries **no artwork**, so [NotificationDealGame.artwork] is joined by game id from
 * the user's waitlist in the repository layer (the only cheap art source).
 */
@Immutable
data class NotificationDetail(
    val notificationId: String,
    val games: List<NotificationDealGame>,
)

/**
 * One game referenced by a waitlist notification, with its current deals. [isExpired] is true when the
 * deal that triggered the notification has lapsed (empty [deals]) — the detail screen still shows the
 * game (with a "deal expired" note) rather than hiding it.
 */
@Immutable
data class NotificationDealGame(
    val gameId: String,
    val title: String,
    val artwork: GameArtwork = GameArtwork(),
    /** The game's all-time-low price, pre-formatted for display (null when ITAD omits it). */
    val historicalLowDenominated: String? = null,
    val deals: List<NotificationShopDeal> = emptyList(),
    /**
     * The id of the ITAD notification entry this game came from. Set when a *day* aggregates several
     * per-game notification entries, so viewing a game's card can mark *its* entry read (per-game read).
     * Null outside the day-detail aggregation.
     */
    val sourceNotificationId: String? = null,
) {
    /** The cheapest current deal, surfaced collapsed before the card expands to all shops. */
    val bestDeal: NotificationShopDeal? get() = deals.minByOrNull { it.salePriceValue }
    val isExpired: Boolean get() = deals.isEmpty()
}

/**
 * A single shop's offer on a notification game, with prices pre-formatted for display ([salePriceValue]
 * is retained only to pick the cheapest). The flags drive the same deal badges as the rest of the app
 * ([isNewHistoricalLow] → "N", [isStoreLow] → "S", [hasVoucher] → scissors).
 */
@Immutable
data class NotificationShopDeal(
    val shopName: String,
    val salePriceValue: Double,
    val salePriceDenominated: String,
    val regularPriceDenominated: String?,
    val cutPercent: Int,
    val url: String,
    val isNewHistoricalLow: Boolean = false,
    val isStoreLow: Boolean = false,
    val hasVoucher: Boolean = false,
)
