import SwiftUI
import UserNotifications
import ComposeApp

@main
struct iOSApp: App {
    // Owns app-launch wiring (Koin, background-task registration, notification taps); see AppDelegate.
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

/**
 * App-launch wiring for the shared (Compose/KMP) layer:
 *  - starts Koin early (so a background launch has a graph before the Compose UI exists),
 *  - registers the `BGTaskScheduler` poll handler — must happen before launch completes,
 *  - routes a tapped notification's `userInfo["route"]` into the shared `NotificationRouteBus` the nav host collects.
 */
class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // Order matters: registration resolves the logger from Koin, so Koin must be up first.
        MainViewControllerKt.startKoinIfNeeded()
        NotificationBackgroundPollKt.registerNotificationBackgroundPoll()
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    // A tapped notification (foreground, background, or cold start) — hand its route to the shared bus.
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
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
