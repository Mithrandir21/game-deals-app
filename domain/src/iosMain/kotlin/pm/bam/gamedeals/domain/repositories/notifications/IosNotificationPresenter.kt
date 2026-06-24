package pm.bam.gamedeals.domain.repositories.notifications

import platform.UserNotifications.UNMutableNotificationContent
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
            )
            analytics.capture(AnalyticsEvents.NOTIFICATION_SHOWN, mapOf("kind" to "franchise", "count" to franchiseGames.size))
        }
    }

    private fun post(identifier: String, title: String, lines: List<String>, fallback: String, route: NotificationRoute) {
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

/** `userInfo` key under which the tap route is stored; the Swift app delegate reads the same literal. */
const val ROUTE_USER_INFO_KEY = "route"
