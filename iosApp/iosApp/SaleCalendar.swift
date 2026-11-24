import Foundation
#if canImport(ActivityKit)
import ActivityKit
#endif

// Shared by the app (the last-hours Live Activity) and the widget extension (the countdown widget and the Live
// Activity's views): it is a member of both targets. Mirrors the Kotlin `SaleCalendarRepository` / `countdownAt`.

/// One big seasonal sale from `https://gamedeals.bam.pm/events/sales.json` (`store/site/sales.json`).
struct BigSale: Equatable {
    let id: String
    /// "Steam", "Epic Games Store" or "GOG".
    let store: String
    let name: String
    let start: Date
    let end: Date
}

/// What a countdown shows: the sale running now, or the next one.
enum SaleCountdown: Equatable {
    case running(BigSale)
    case upcoming(BigSale)

    var sale: BigSale {
        switch self {
        case .running(let sale), .upcoming(let sale): return sale
        }
    }

    /// When the count reaches zero: the end of a running sale, the start of an upcoming one.
    var target: Date {
        switch self {
        case .running(let sale): return sale.end
        case .upcoming(let sale): return sale.start
        }
    }
}

enum SaleCalendar {
    static let url = URL(string: "https://gamedeals.bam.pm/events/sales.json")!

    /// The sales, or nil when the file couldn't be fetched or read. Public data; nothing about the user is sent.
    static func fetch() async -> [BigSale]? {
        var request = URLRequest(url: url)
        request.timeoutInterval = 15
        guard let (data, response) = try? await URLSession.shared.data(for: request),
              (response as? HTTPURLResponse)?.statusCode == 200 else { return nil }
        return decode(data)
    }

    private struct Document: Decodable {
        let schemaVersion: Int
        let sales: [RawSale]
    }

    private struct RawSale: Decodable {
        let id: String
        let store: String
        let name: String
        let start: String
        let end: String
    }

    /// Reads the file; nil for an unknown schema. A sale with an unreadable date, or ending before it starts, is skipped.
    static func decode(_ data: Data) -> [BigSale]? {
        guard let document = try? JSONDecoder().decode(Document.self, from: data), document.schemaVersion == 1 else { return nil }
        let dates = ISO8601DateFormatter()
        return document.sales.compactMap { raw in
            guard let start = dates.date(from: raw.start), let end = dates.date(from: raw.end), start < end,
                  !raw.name.trimmingCharacters(in: .whitespaces).isEmpty else { return nil }
            return BigSale(id: raw.id, store: raw.store, name: raw.name, start: start, end: end)
        }
    }

    /// The running sale that ends soonest, else the next to start; nil when there is neither.
    static func countdown(_ sales: [BigSale], at now: Date) -> SaleCountdown? {
        if let running = sales.filter({ $0.start <= now && now < $0.end }).min(by: { $0.end < $1.end }) {
            return .running(running)
        }
        return sales.filter { $0.start > now }.min(by: { $0.start < $1.start }).map { .upcoming($0) }
    }
}

#if canImport(ActivityKit)
/// The lock-screen countdown for a sale's last hours, started by the app (`SaleLiveActivity`) and drawn by the widget
/// extension (`SaleCountdownLiveActivity`).
@available(iOS 16.1, *)
struct SaleCountdownAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        var endsAt: Date
    }

    var saleId: String
    var saleName: String
    var store: String
}
#endif
