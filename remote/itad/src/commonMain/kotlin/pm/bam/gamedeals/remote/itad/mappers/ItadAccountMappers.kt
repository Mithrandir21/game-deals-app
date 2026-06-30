package pm.bam.gamedeals.remote.itad.mappers

import kotlin.time.Instant
import pm.bam.gamedeals.domain.models.CollectionEntry
import pm.bam.gamedeals.domain.models.IgnoredEntry
import pm.bam.gamedeals.domain.models.ItadNote
import pm.bam.gamedeals.domain.models.ItadNotification
import pm.bam.gamedeals.domain.models.ItadUser
import pm.bam.gamedeals.domain.models.NotificationDealGame
import pm.bam.gamedeals.domain.models.NotificationDetail
import pm.bam.gamedeals.domain.models.NotificationGame
import pm.bam.gamedeals.domain.models.NotificationShopDeal
import pm.bam.gamedeals.domain.models.WaitlistEntry
import pm.bam.gamedeals.domain.utils.formatMoney
import pm.bam.gamedeals.remote.itad.models.RemoteItadDealEntry
import pm.bam.gamedeals.remote.itad.models.RemoteItadNote
import pm.bam.gamedeals.remote.itad.models.RemoteItadNotification
import pm.bam.gamedeals.remote.itad.models.RemoteItadNotificationGame
import pm.bam.gamedeals.remote.itad.models.RemoteItadNotificationWaitlist
import pm.bam.gamedeals.remote.itad.models.RemoteItadSearchGame
import pm.bam.gamedeals.remote.itad.models.RemoteItadUser
import pm.bam.gamedeals.remote.itad.models.toGameArtwork

internal fun RemoteItadUser.toItadUser(): ItadUser = ItadUser(username = username)

internal fun RemoteItadNotification.toItadNotification(): ItadNotification =
    ItadNotification(id = id, type = type, title = title, timestamp = timestamp, read = read != null)

internal fun RemoteItadNote.toItadNote(): ItadNote = ItadNote(gameId = gid, note = note)

internal fun RemoteItadNotificationGame.toNotificationGame(): NotificationGame =
    NotificationGame(gameId = id, title = title)

/**
 * Map a waitlist-notification detail into the domain [NotificationDetail] for the in-app detail screen.
 * [notificationId] is taken from the request (the payload echoes it, but the caller's id is authoritative).
 * Artwork is left empty here — joined by game id from the user's waitlist in the repository layer.
 */
internal fun RemoteItadNotificationWaitlist.toNotificationDetail(notificationId: String): NotificationDetail =
    NotificationDetail(
        notificationId = notificationId,
        games = games.map { it.toNotificationDealGame() },
    )

private fun RemoteItadNotificationGame.toNotificationDealGame(): NotificationDealGame =
    NotificationDealGame(
        gameId = id,
        title = title,
        historicalLowDenominated = historyLow?.let { formatMoney(it.amount, it.currency) },
        deals = deals.map { it.toNotificationShopDeal() },
    )

private fun RemoteItadDealEntry.toNotificationShopDeal(): NotificationShopDeal =
    NotificationShopDeal(
        shopName = shop.name,
        salePriceValue = price.amount,
        salePriceDenominated = formatMoney(price.amount, price.currency),
        regularPriceDenominated = regular?.let { formatMoney(it.amount, it.currency) },
        cutPercent = cut,
        url = url,
        isNewHistoricalLow = flag.isNewHistoryLowFlag(),
        isStoreLow = flag.isStoreLowFlag(),
        hasVoucher = voucher.isVoucherPresent(),
    )

internal fun RemoteItadSearchGame.toWaitlistEntry(): WaitlistEntry =
    WaitlistEntry(
        gameId = id,
        title = title,
        artwork = assets.toGameArtwork(),
        type = type,
        mature = mature ?: false,
        addedEpochMs = added.toEpochMsOrNull(),
    )

internal fun RemoteItadSearchGame.toCollectionEntry(): CollectionEntry =
    CollectionEntry(
        gameId = id,
        title = title,
        artwork = assets.toGameArtwork(),
        type = type,
        mature = mature ?: false,
        group = group,
        addedEpochMs = added.toEpochMsOrNull(),
    )

/** ISO-8601 (with offset) → epoch millis; unparseable/null → null. Mirrors the bundle date idiom. */
private fun String?.toEpochMsOrNull(): Long? =
    this?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() }

internal fun RemoteItadSearchGame.toIgnoredEntry(): IgnoredEntry =
    IgnoredEntry(gameId = id, title = title, artwork = assets.toGameArtwork())
