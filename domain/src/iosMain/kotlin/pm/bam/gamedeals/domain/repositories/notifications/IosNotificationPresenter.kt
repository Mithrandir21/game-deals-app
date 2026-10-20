package pm.bam.gamedeals.domain.repositories.notifications

import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationAction
import platform.UserNotifications.UNNotificationActionOptionForeground
import platform.UserNotifications.UNNotificationActionOptionNone
import platform.UserNotifications.UNNotificationCategory
import platform.UserNotifications.UNNotificationCategoryOptionNone
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter
import pm.bam.gamedeals.common.navigation.NotificationRoute
import pm.bam.gamedeals.domain.models.NotificationDealGame
import pm.bam.gamedeals.domain.models.mergedByGameId
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.analytics.Analytics
import pm.bam.gamedeals.logging.analytics.AnalyticsEvents
import pm.bam.gamedeals.logging.error

/**
 * iOS [NotificationPresenter] (#7 notification revamp). The mirror of `AndroidNotificationPresenter`: posts
 * **at most two** `UNUserNotificationCenter` notifications per poll — one bundled summary per path — and tags
 * each with its [NotificationRoute] key in `userInfo` so the app delegate can route a tap back into the nav
 * graph via `NotificationRouteBus`. iOS has no `InboxStyle`, so the per-game lines are joined into the body.
 * Each summary carries a category ([registerNotificationCategories]) so iOS shows its action buttons.
 */
internal class IosNotificationPresenter(
    private val logger: Logger,
    private val analytics: Analytics,
) : NotificationPresenter {

    override suspend fun present(alerts: List<PendingNotificationAlert>) {
        if (alerts.isEmpty()) return
        // A followed-franchise alert carries a gameId (and no games); an ITAD waitlist alert carries games.
        val (franchiseAlerts, waitlistAlerts) = alerts.partition { it.gameId != null }

        if (waitlistAlerts.isNotEmpty()) {
            // One line per game — a game that dropped several times this poll collapses to its cheapest deal.
            val games = waitlistAlerts.flatMap { it.games }.mergedByGameId()
            post(
                identifier = WAITLIST_SUMMARY_ID,
                title = "Waitlist price drops",
                lines = games.map { it.toLine() },
                fallback = "${waitlistAlerts.size} new deals",
                route = NotificationRoute.Notifications,
                category = WAITLIST_CATEGORY_ID,
            )
            analytics.capture(AnalyticsEvents.NOTIFICATION_SHOWN, mapOf("kind" to "waitlist", "count" to games.size))
        }
        if (franchiseAlerts.isNotEmpty()) {
            // Each franchise alert's title already self-describes ("X is Y% off in Z"); one line per game.
            val franchiseGames = franchiseAlerts.distinctBy { it.gameId }
            post(
                identifier = FRANCHISE_SUMMARY_ID,
                title = "Followed-series deals",
                lines = franchiseGames.map { it.title },
                fallback = "${franchiseAlerts.size} new deals",
                route = NotificationRoute.FollowedSeries,
                category = FRANCHISE_CATEGORY_ID,
            )
            analytics.capture(AnalyticsEvents.NOTIFICATION_SHOWN, mapOf("kind" to "franchise", "count" to franchiseGames.size))
        }
    }

    private fun post(
        identifier: String,
        title: String,
        lines: List<String>,
        fallback: String,
        route: NotificationRoute,
        category: String,
    ) {
        val body = when {
            lines.isEmpty() -> fallback
            lines.size <= MAX_GAME_LINES -> lines.joinToString("\n")
            else -> (lines.take(MAX_GAME_LINES) + "+${lines.size - MAX_GAME_LINES} more").joinToString("\n")
        }
        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(body)
            setSound(UNNotificationSound.defaultSound)
            setUserInfo(mapOf<Any?, Any?>(ROUTE_USER_INFO_KEY to route.key))
            setCategoryIdentifier(category)
        }
        // Reusing a stable identifier replaces an undelivered summary rather than stacking copies.
        val request = UNNotificationRequest.requestWithIdentifier(identifier, content, trigger = null)
        UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(request) { err ->
            if (err != null) error(logger) { "Failed to post '$identifier' notification: ${err.localizedDescription}" }
        }
    }

    private fun NotificationDealGame.toLine(): String {
        val best = bestDeal
        return if (best != null) "$title · -${best.cutPercent}% · ${best.salePriceDenominated}" else "$title · deal expired"
    }

    private companion object {
        const val WAITLIST_SUMMARY_ID = "waitlist_summary"
        const val FRANCHISE_SUMMARY_ID = "franchise_summary"
        const val MAX_GAME_LINES = 6
    }
}

/**
 * Registers the action buttons for the two alert summaries. Call once at launch, before any alert is posted;
 * re-registering replaces the set, so calling it on every launch is harmless.
 *  - Waitlist price drops: "View deals" opens the Notifications screen; "Mark as read" marks every ITAD
 *    notification read without opening the app (the app delegate routes it to `markAllNotificationsRead`).
 *  - Followed-series deals: "View series" opens Followed series. There is nothing to mark read: these alerts are
 *    worked out on the device, not stored by ITAD.
 * The default tap still routes via `userInfo[route]`, so these only add choices.
 */
fun registerNotificationCategories() {
    val waitlist = UNNotificationCategory.categoryWithIdentifier(
        identifier = WAITLIST_CATEGORY_ID,
        actions = listOf(
            UNNotificationAction.actionWithIdentifier(VIEW_ACTION_ID, "View deals", UNNotificationActionOptionForeground),
            UNNotificationAction.actionWithIdentifier(MARK_READ_ACTION_ID, "Mark as read", UNNotificationActionOptionNone),
        ),
        intentIdentifiers = emptyList<String>(),
        options = UNNotificationCategoryOptionNone,
    )
    val franchise = UNNotificationCategory.categoryWithIdentifier(
        identifier = FRANCHISE_CATEGORY_ID,
        actions = listOf(
            UNNotificationAction.actionWithIdentifier(VIEW_ACTION_ID, "View series", UNNotificationActionOptionForeground),
        ),
        intentIdentifiers = emptyList<String>(),
        options = UNNotificationCategoryOptionNone,
    )
    UNUserNotificationCenter.currentNotificationCenter().setNotificationCategories(setOf(waitlist, franchise))
}

private const val WAITLIST_CATEGORY_ID = "waitlist_alert"
private const val FRANCHISE_CATEGORY_ID = "franchise_alert"
private const val VIEW_ACTION_ID = "view"

/** Action id of the waitlist alert's "Mark as read" button; the Swift app delegate reads the same literal. */
const val MARK_READ_ACTION_ID = "mark_read"

/** `userInfo` key under which the tap route is stored; the Swift app delegate reads the same literal. */
const val ROUTE_USER_INFO_KEY = "route"
