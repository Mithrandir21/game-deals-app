import SwiftUI
import WidgetKit

// Home Screen widget: how long until the next big Steam, Epic or GOG sale starts, or how long the current one has
// left. Fetches the Worker's public sales.json itself (SaleCalendar, shared with the app), so it needs no App Group.
// The count is drawn with relative-date text, which the system keeps current without new timeline entries; the
// timeline only adds entries for the moments a sale starts or ends. Tapping opens the app's Deals tab.

struct SaleCountdownWidget: Widget {
    let kind = "SaleCountdownWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: SaleCountdownProvider()) { entry in
            SaleCountdownView(entry: entry)
                .widgetURL(URL(string: "pm.bam.gamedeals://deals"))
                .widgetBackground()
        }
        .configurationDisplayName("Sale countdown")
        .description("How long until the next big Steam, Epic or GOG sale starts, and how long the current one has left.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

struct SaleCountdownEntry: TimelineEntry {
    let date: Date
    let countdown: SaleCountdown?
    /// The file couldn't be fetched, so there is nothing to show.
    let failed: Bool
}

struct SaleCountdownProvider: TimelineProvider {
    /// The dates rarely change; fetch them again after this long.
    private static let refreshAfter: TimeInterval = 6 * 60 * 60

    func placeholder(in context: Context) -> SaleCountdownEntry {
        SaleCountdownEntry(date: Date(), countdown: .running(Self.sample), failed: false)
    }

    func getSnapshot(in context: Context, completion: @escaping (SaleCountdownEntry) -> Void) {
        if context.isPreview {
            completion(placeholder(in: context))
            return
        }
        Task {
            let sales = await SaleCalendar.fetch()
            let now = Date()
            completion(SaleCountdownEntry(date: now, countdown: sales.flatMap { SaleCalendar.countdown($0, at: now) }, failed: sales == nil))
        }
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<SaleCountdownEntry>) -> Void) {
        Task {
            let sales = await SaleCalendar.fetch()
            let now = Date()
            let refresh = now.addingTimeInterval(Self.refreshAfter)
            var entries = [SaleCountdownEntry(date: now, countdown: sales.flatMap { SaleCalendar.countdown($0, at: now) }, failed: sales == nil)]
            // A new entry each time a sale starts or ends before the next fetch, so "Starts in" turns into "Ends in".
            let changes = (sales ?? []).flatMap { [$0.start, $0.end] }.filter { $0 > now && $0 < refresh }
            for change in Set(changes).sorted() {
                entries.append(SaleCountdownEntry(date: change, countdown: SaleCalendar.countdown(sales ?? [], at: change), failed: false))
            }
            completion(Timeline(entries: entries, policy: .after(refresh)))
        }
    }

    static let sample = BigSale(
        id: "sample", store: "Steam", name: "Steam Winter Sale",
        start: Date().addingTimeInterval(-2 * 86_400), end: Date().addingTimeInterval(5 * 86_400)
    )
}

struct SaleCountdownView: View {
    let entry: SaleCountdownEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Label("Big sales", systemImage: "timer")
                .font(.caption.bold())
                .foregroundStyle(Color.accentColor)
            if let countdown = entry.countdown {
                Text(countdown.sale.name)
                    .font(.subheadline.bold())
                    .lineLimit(2)
                Group {
                    switch countdown {
                    case .running: Text("Ends in ") + Text(countdown.target, style: .relative)
                    case .upcoming: Text("Starts in ") + Text(countdown.target, style: .relative)
                    }
                }
                .font(.headline)
                .foregroundStyle(Color.accentColor)
                .lineLimit(2)
                Text("\(countdown.sale.store) · \(countdown.target.formatted(.dateTime.month(.abbreviated).day()))")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            } else {
                Text(entry.failed ? "Couldn't load the sale dates. Tap to open the app." : "No big sale announced yet.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
