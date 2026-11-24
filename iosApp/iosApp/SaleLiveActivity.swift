import Foundation
#if canImport(ActivityKit)
import ActivityKit
#endif

/// Starts a lock-screen countdown when a big sale has under eight hours left and the app comes to the foreground, and
/// ends countdowns whose sale is over. Apple caps a Live Activity at about eight hours and only the app can start one,
/// so this covers a sale's last day, not the whole sale. No push: the countdown runs on the phone's clock.
enum SaleLiveActivity {
    /// Apple ends a Live Activity after eight hours, so start only within that window.
    static let window: TimeInterval = 8 * 60 * 60

    static func refresh() {
        #if canImport(ActivityKit)
        guard #available(iOS 16.2, *) else { return }
        Task {
            let now = Date()
            for activity in Activity<SaleCountdownAttributes>.activities where activity.content.state.endsAt <= now {
                await activity.end(nil, dismissalPolicy: .immediate)
            }
            guard ActivityAuthorizationInfo().areActivitiesEnabled,
                  let sales = await SaleCalendar.fetch(),
                  case .running(let sale)? = SaleCalendar.countdown(sales, at: now),
                  sale.end.timeIntervalSince(now) <= window,
                  !Activity<SaleCountdownAttributes>.activities.contains(where: { $0.attributes.saleId == sale.id })
            else { return }
            let attributes = SaleCountdownAttributes(saleId: sale.id, saleName: sale.name, store: sale.store)
            let content = ActivityContent(state: SaleCountdownAttributes.ContentState(endsAt: sale.end), staleDate: sale.end)
            _ = try? Activity.request(attributes: attributes, content: content, pushType: nil)
        }
        #endif
    }
}
