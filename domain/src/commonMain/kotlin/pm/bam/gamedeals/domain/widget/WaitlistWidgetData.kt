package pm.bam.gamedeals.domain.widget

import kotlinx.serialization.Serializable
import pm.bam.gamedeals.domain.models.WaitlistDisplaySnapshot
import pm.bam.gamedeals.domain.models.onSale
import pm.bam.gamedeals.domain.models.thumbnail

/**
 * What the iOS Waitlist deals widget shows, written by the app as JSON into the shared App Group container
 * (`WaitlistWidgetExport`, iosMain) and decoded by the Swift widget (`iosApp/GameDealsWidget/WaitlistDealsWidget.swift`).
 * The field names are that file format, so renaming one means changing the Swift `Codable` type too.
 * The Android widget reads the repositories directly and doesn't use this.
 */
@Serializable
data class WaitlistWidgetData(
    val signedIn: Boolean,
    /** False while signed in but no priced snapshot exists yet (the app hasn't loaded the waitlist). */
    val loaded: Boolean,
    /** Games on the waitlist; 0 shows the "waitlist is empty" state. */
    val waitlistCount: Int,
    /** On-sale games, biggest discount first, at most [MAX_WAITLIST_WIDGET_DEALS]. */
    val deals: List<WaitlistWidgetDeal>,
    val refreshedAtEpochMs: Long,
)

@Serializable
data class WaitlistWidgetDeal(
    val gameId: String,
    val title: String,
    /** e.g. "$4.99". */
    val price: String,
    /** The usual price, or null when unknown or the same as [price]. */
    val regular: String?,
    val discountPercent: Int,
    val shop: String?,
    val atHistoricalLow: Boolean,
    val thumbnailUrl: String?,
)

/** Matches the Android widget's largest size. */
const val MAX_WAITLIST_WIDGET_DEALS = 7

/**
 * Builds the widget's data from the app's state. Rows that left the waitlist since the snapshot was priced are dropped
 * (the snapshot isn't refetched on removal), and prices for a region other than [region] are never shown.
 */
fun buildWaitlistWidgetData(
    signedIn: Boolean,
    region: String,
    snapshot: WaitlistDisplaySnapshot?,
    waitlistedIds: Set<String>,
): WaitlistWidgetData {
    if (!signedIn) return WaitlistWidgetData(signedIn = false, loaded = false, waitlistCount = 0, deals = emptyList(), refreshedAtEpochMs = 0)
    if (snapshot == null) {
        return WaitlistWidgetData(signedIn = true, loaded = false, waitlistCount = waitlistedIds.size, deals = emptyList(), refreshedAtEpochMs = 0)
    }
    val items = snapshot.items.filter { it.gameId in waitlistedIds }
    val onSale = if (snapshot.regionCode == region) items.onSale() else emptyList()
    return WaitlistWidgetData(
        signedIn = true,
        loaded = true,
        waitlistCount = items.size,
        deals = onSale.take(MAX_WAITLIST_WIDGET_DEALS).map { item ->
            WaitlistWidgetDeal(
                gameId = item.gameId,
                title = item.title,
                price = item.bestPriceDenominated.orEmpty(),
                regular = item.bestRegularDenominated?.takeIf { it != item.bestPriceDenominated },
                discountPercent = item.discountPercent ?: 0,
                shop = item.bestShopName,
                atHistoricalLow = item.isAtHistoricalLow,
                thumbnailUrl = item.artwork.thumbnail,
            )
        },
        refreshedAtEpochMs = snapshot.refreshedAtEpochMs,
    )
}
