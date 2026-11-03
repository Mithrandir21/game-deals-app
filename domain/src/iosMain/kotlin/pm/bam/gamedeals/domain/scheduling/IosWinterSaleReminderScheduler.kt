package pm.bam.gamedeals.domain.scheduling

import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import pm.bam.gamedeals.common.navigation.NotificationRoute
import pm.bam.gamedeals.domain.repositories.notifications.ROUTE_USER_INFO_KEY
import pm.bam.gamedeals.domain.repositories.wintersale.WinterSaleReminderScheduler
import pm.bam.gamedeals.logging.Logger
import pm.bam.gamedeals.logging.error

/**
 * A pending local notification under one stable identifier, so re-adding replaces it. It fires without running app
 * code, so a remote disable only takes effect if the app opens or the background check runs first (accepted).
 */
internal class IosWinterSaleReminderScheduler(private val logger: Logger) : WinterSaleReminderScheduler {

    override fun schedule(delayMillis: Long) {
        val content = UNMutableNotificationContent().apply {
            setTitle("The Winter Sale is on")
            setBody("See discounted games from your waitlist, followed series and popular picks.")
            setSound(UNNotificationSound.defaultSound)
            setUserInfo(mapOf<Any?, Any?>(ROUTE_USER_INFO_KEY to NotificationRoute.WinterSaleGuide.key))
        }
        // The interval trigger counts from now, so a wrong device clock can't move the reminder.
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval((delayMillis / 1_000.0).coerceAtLeast(1.0), repeats = false)
        val request = UNNotificationRequest.requestWithIdentifier(REMINDER_ID, content, trigger)
        UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(request) { err ->
            if (err != null) error(logger) { "Failed to schedule the Winter Sale reminder: ${err.localizedDescription}" }
        }
    }

    override fun cancel() {
        UNUserNotificationCenter.currentNotificationCenter().removePendingNotificationRequestsWithIdentifiers(listOf(REMINDER_ID))
    }

    private companion object {
        const val REMINDER_ID = "winter_sale_reminder"
    }
}
