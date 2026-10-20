import SwiftUI
import UserNotifications
import WidgetKit
import ComposeApp

@main
struct iOSApp: App {
    // Owns app-launch wiring (Koin, background-task registration, notification taps); see AppDelegate.
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
                // Widget links: `pm.bam.gamedeals://giveaways` (Free Games), `://waitlist` and `://game/<id>` (Waitlist
                // deals). The same scheme carries the ITAD OAuth redirect, but ASWebAuthenticationSession consumes that one
                // itself, so it never lands here.
                .onOpenURL { url in
                    switch url.host {
                    case "giveaways": AppShortcutsKt.openGiveawaysShortcut()
                    case "waitlist": AppShortcutsKt.openWaitlistShortcut()
                    case "game": AppShortcutsKt.openGameShortcut(gameId: url.lastPathComponent)
                    default: break
                    }
                }
        }
    }
}

/**
 * App-launch wiring for the shared (Compose/KMP) layer:
 *  - starts Koin early (so a background launch has a graph before the Compose UI exists),
 *  - registers the `BGTaskScheduler` poll handler — must happen before launch completes,
 *  - registers the sale-alert action buttons,
 *  - routes a tapped notification's `userInfo["route"]` into the shared `NotificationRouteBus` the nav host collects,
 *    and runs the "Mark as read" action without opening the app.
 */
class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // Order matters: Sentry first so its crash handler arms before any other startup work can throw; then
        // Koin (registration resolves the logger from it), then the background-poll registration.
        MainViewControllerKt.startSentry()
        MainViewControllerKt.startKoinIfNeeded()
        NotificationBackgroundPollKt.registerNotificationBackgroundPoll()
        IosNotificationPresenterKt.registerNotificationCategories()
        // Keep the Waitlist deals widget's shared file current, and refresh stale prices only if one is placed.
        WaitlistWidgetExportKt.startWaitlistWidgetExport(reloadWidget: {
            WidgetCenter.shared.reloadTimelines(ofKind: WaitlistDealsKind)
        })
        WidgetCenter.shared.getCurrentConfigurations { result in
            if case .success(let widgets) = result, widgets.contains(where: { $0.kind == WaitlistDealsKind }) {
                WaitlistWidgetExportKt.onWaitlistWidgetInstalled()
            }
        }
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    // A tapped notification (foreground, background, or cold start) — hand its route to the shared bus. "Mark as
    // read" runs in the background instead; iOS waits for completionHandler before suspending the app again.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        if response.actionIdentifier == IosNotificationPresenterKt.MARK_READ_ACTION_ID {
            // onDone arrives on a Kotlin background thread; iOS asserts the completion handler runs on main.
            MainViewControllerKt.markAllNotificationsRead(onDone: { DispatchQueue.main.async { completionHandler() } })
            return
        }
        // The default tap, "View deals" and "View series" all open the summary's screen.
        let routeKey = response.notification.request.content.userInfo["route"] as? String
        MainViewControllerKt.deliverNotificationRoute(routeKey: routeKey)
        completionHandler()
    }

    // Show our summaries while the app is foregrounded too (iOS suppresses them by default).
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .list, .sound])
    }
}

/** WidgetKit kind of the Waitlist deals widget; must match `WaitlistDealsWidget.kind` in the widget extension. */
private let WaitlistDealsKind = "WaitlistDealsWidget"
