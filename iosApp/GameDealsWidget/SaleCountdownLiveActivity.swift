#if canImport(ActivityKit)
import ActivityKit
import SwiftUI
import WidgetKit

// The lock screen and Dynamic Island views of a big sale's last-hours countdown. The app starts and ends the
// activity (SaleLiveActivity); this only draws it. The timer text counts down on the phone's own clock.

@available(iOS 16.1, *)
struct SaleCountdownLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: SaleCountdownAttributes.self) { context in
            HStack(alignment: .center, spacing: 12) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(context.attributes.saleName)
                        .font(.headline)
                        .lineLimit(1)
                    Text("\(context.attributes.store) · last hours")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 2) {
                    Text("Ends in")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                    countdown(to: context.state.endsAt)
                        .font(.title2.bold())
                }
            }
            .padding()
            .widgetURL(URL(string: "pm.bam.gamedeals://deals"))
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Text(context.attributes.saleName)
                        .font(.headline)
                        .lineLimit(1)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    countdown(to: context.state.endsAt)
                        .font(.headline)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    Text("The \(context.attributes.store) sale ends soon")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            } compactLeading: {
                Image(systemName: "timer")
            } compactTrailing: {
                countdown(to: context.state.endsAt)
                    .frame(maxWidth: 52)
            } minimal: {
                Image(systemName: "timer")
            }
            .widgetURL(URL(string: "pm.bam.gamedeals://deals"))
        }
    }

    /// A timer counting down to [end]; an empty range once it has passed, so the text stops at zero instead of failing.
    private func countdown(to end: Date) -> some View {
        let now = Date()
        return Text(timerInterval: now...max(now, end), countsDown: true)
            .monospacedDigit()
            .multilineTextAlignment(.trailing)
    }
}
#endif
