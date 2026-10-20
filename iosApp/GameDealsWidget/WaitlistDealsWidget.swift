import SwiftUI
import UIKit
import WidgetKit

// Home Screen widget: the games on the user's IsThereAnyDeal waitlist that are on sale right now. Unlike Free Games it
// can't fetch anything itself (prices need the user's sign-in), so the app writes `waitlist-deals.json` into the shared
// App Group container whenever the waitlist changes and reloads this widget (WaitlistWidgetExport.kt). The widget only
// reads that file. Tapping a game opens its page (`pm.bam.gamedeals://game/<id>`); anywhere else opens the Waitlist.
// The Android twin is widget/WaitlistDealsWidget.kt.

struct WaitlistDealsWidget: Widget {
    /// Must match `WaitlistDealsKind` in iOSApp.swift, which reloads this widget.
    let kind = "WaitlistDealsWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: WaitlistDealsProvider()) { entry in
            WaitlistDealsWidgetView(entry: entry)
                .widgetBackground()
        }
        .configurationDisplayName("Waitlist Deals")
        .description("Games on your waitlist that are on sale right now.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}

// MARK: - Data

/// The file the app writes; mirrors `WaitlistWidgetData` in domain/.../widget/WaitlistWidgetData.kt.
struct WaitlistWidgetData: Decodable {
    let signedIn: Bool
    let loaded: Bool
    let waitlistCount: Int
    let deals: [WaitlistWidgetDeal]
    let refreshedAtEpochMs: Int64
}

struct WaitlistWidgetDeal: Decodable {
    let gameId: String
    let title: String
    let price: String
    let regular: String?
    let discountPercent: Int
    let shop: String?
    let atHistoricalLow: Bool
    let thumbnailUrl: String?
}

struct WaitlistDeal: Identifiable {
    let deal: WaitlistWidgetDeal
    let thumbnail: UIImage?
    var id: String { deal.gameId }
}

enum WaitlistDealsState {
    /// No file yet (the app hasn't run since installing the widget) or signed out.
    case signedOut
    /// Signed in but the app hasn't loaded the waitlist yet.
    case notLoaded
    case emptyWaitlist
    /// Empty when nothing on the waitlist is discounted right now.
    case loaded([WaitlistDeal])
}

struct WaitlistDealsEntry: TimelineEntry {
    let date: Date
    let state: WaitlistDealsState
}

struct WaitlistDealsProvider: TimelineProvider {
    func placeholder(in context: Context) -> WaitlistDealsEntry {
        WaitlistDealsEntry(date: Date(), state: .loaded(WaitlistDealsProvider.sample))
    }

    func getSnapshot(in context: Context, completion: @escaping (WaitlistDealsEntry) -> Void) {
        if context.isPreview {
            completion(placeholder(in: context))
            return
        }
        Task { completion(WaitlistDealsEntry(date: Date(), state: await WaitlistDealsFile.read(limit: context.family.rows))) }
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<WaitlistDealsEntry>) -> Void) {
        Task {
            let state = await WaitlistDealsFile.read(limit: context.family.rows)
            // The app reloads this widget whenever the file changes; the periodic re-read only picks up thumbnails that
            // failed to download last time.
            let next = Date().addingTimeInterval(60 * 60)
            completion(Timeline(entries: [WaitlistDealsEntry(date: Date(), state: state)], policy: .after(next)))
        }
    }

    static let sample: [WaitlistDeal] = [
        WaitlistDeal(deal: WaitlistWidgetDeal(gameId: "1", title: "Starfall Odyssey", price: "$4.99", regular: "$19.99",
                                              discountPercent: 75, shop: "Steam", atHistoricalLow: true, thumbnailUrl: nil), thumbnail: nil),
        WaitlistDeal(deal: WaitlistWidgetDeal(gameId: "2", title: "Pixel Dungeon Quest", price: "$3.99", regular: "$9.99",
                                              discountPercent: 60, shop: "GOG", atHistoricalLow: false, thumbnailUrl: nil), thumbnail: nil),
        WaitlistDeal(deal: WaitlistWidgetDeal(gameId: "3", title: "Neon Drift Racer", price: "$7.49", regular: "$14.99",
                                              discountPercent: 50, shop: "Humble", atHistoricalLow: true, thumbnailUrl: nil), thumbnail: nil),
    ]
}

private extension WidgetFamily {
    var rows: Int {
        switch self {
        case .systemSmall: return 1
        case .systemMedium: return 3
        default: return 7
        }
    }
}

enum WaitlistDealsFile {
    /// Same App Group and file name as `APP_GROUP_ID` / `WAITLIST_WIDGET_FILE` in WaitlistWidgetExport.kt.
    static let appGroup = "group.pm.bam.gamedeals"
    static let fileName = "waitlist-deals.json"

    static func read(limit: Int) async -> WaitlistDealsState {
        guard let container = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroup),
              let data = try? Data(contentsOf: container.appendingPathComponent(fileName)),
              let file = try? JSONDecoder().decode(WaitlistWidgetData.self, from: data),
              file.signedIn else { return .signedOut }
        if !file.loaded { return .notLoaded }
        if file.waitlistCount == 0 { return .emptyWaitlist }
        var deals: [WaitlistDeal] = []
        for deal in file.deals.prefix(limit) {
            deals.append(WaitlistDeal(deal: deal, thumbnail: await WidgetImages.thumbnail(deal.thumbnailUrl)))
        }
        return .loaded(deals)
    }
}

// MARK: - Views

struct WaitlistDealsWidgetView: View {
    let entry: WaitlistDealsEntry
    @Environment(\.widgetFamily) private var family

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Label(family == .systemSmall ? "Waitlist deals" : "On sale from your waitlist", systemImage: "bookmark.fill")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.tint)
                .lineLimit(1)
            content
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        // One widgetURL per widget (several is undefined). A small widget has a single tap target, so with a deal it
        // opens that game; otherwise the Waitlist, which offers sign-in when signed out. Medium and large rows are Links.
        .widgetURL(tapURL)
    }

    private var tapURL: URL? {
        if family == .systemSmall, case .loaded(let deals) = entry.state, let first = deals.first {
            return gameURL(first.deal.gameId)
        }
        return URL(string: "pm.bam.gamedeals://waitlist")
    }

    @ViewBuilder private var content: some View {
        switch entry.state {
        case .signedOut:
            Message(text: "Sign in with IsThereAnyDeal to see your waitlist deals. Tap to sign in.")
        case .notLoaded:
            Message(text: "Open the app to load your waitlist.")
        case .emptyWaitlist:
            Message(text: "Your waitlist is empty. Add games from their pages to see their deals here.")
        case .loaded(let deals) where deals.isEmpty:
            Message(text: "Nothing on your waitlist is on sale right now.")
        case .loaded(let deals):
            if family == .systemSmall, let first = deals.first {
                SmallDeal(deal: first)
            } else {
                ForEach(deals) { deal in
                    Link(destination: gameURL(deal.deal.gameId) ?? URL(string: "pm.bam.gamedeals://waitlist")!) {
                        DealRow(deal: deal)
                    }
                }
            }
        }
    }

    private func gameURL(_ gameId: String) -> URL? {
        URL(string: "pm.bam.gamedeals://game/\(gameId.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? gameId)")
    }
}

private struct Message: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.footnote)
            .foregroundStyle(.secondary)
    }
}

private struct SmallDeal: View {
    let deal: WaitlistDeal

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Thumbnail(image: deal.thumbnail)
                .frame(maxWidth: .infinity)
                .frame(height: 56)
            Text(deal.deal.title)
                .font(.footnote.weight(.semibold))
                .lineLimit(2)
            PriceLine(deal: deal.deal)
            if deal.deal.atHistoricalLow { LowestEver() }
        }
    }
}

private struct DealRow: View {
    let deal: WaitlistDeal

    var body: some View {
        HStack(spacing: 8) {
            Thumbnail(image: deal.thumbnail)
                .frame(width: 64, height: 32)
            VStack(alignment: .leading, spacing: 1) {
                Text(deal.deal.title)
                    .font(.footnote.weight(.semibold))
                    .foregroundStyle(.primary)
                    .lineLimit(1)
                HStack(spacing: 6) {
                    PriceLine(deal: deal.deal)
                    if deal.deal.atHistoricalLow { LowestEver() }
                }
            }
        }
    }
}

/// "-75% $4.99 ~~$19.99~~ Steam"
private struct PriceLine: View {
    let deal: WaitlistWidgetDeal

    var body: some View {
        HStack(spacing: 4) {
            Text("-\(deal.discountPercent)%")
                .fontWeight(.bold)
                .foregroundStyle(.tint)
            Text(deal.price)
                .foregroundStyle(.primary)
            if let regular = deal.regular {
                Text(regular).strikethrough()
            }
            if let shop = deal.shop {
                Text(shop)
            }
        }
        .font(.caption2)
        .foregroundStyle(.secondary)
        .lineLimit(1)
    }
}

private struct LowestEver: View {
    var body: some View {
        Text("Lowest ever")
            .font(.caption2.weight(.bold))
            .foregroundStyle(.purple)
            .lineLimit(1)
    }
}
