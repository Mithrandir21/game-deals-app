import Foundation
#if canImport(ActivityKit)
import ActivityKit
#endif

// Shared by the app (the last-hours Live Activity) and the widget extension (the countdown widget and the Live
// Activity's views): it is a member of both targets. Mirrors the Kotlin `SaleCalendarRepository` / `countdownAt`.

/// How much a sale matters to the countdown.
enum SaleKind: Equatable {
    /// A store-wide seasonal sale (Steam Winter, Epic Holiday).
    case big
    /// A smaller themed sale (a Steam fest); it gives way to a big sale about to start.
    case fest
}

/// One sale from `https://gamedeals.bam.pm/events/sales.json` (`store/site/sales.json`).
struct StoreSale: Equatable {
    let id: String
    /// "Steam", "Epic Games Store" or "GOG".
    let store: String
    let name: String
    let start: Date
    let end: Date
    var kind: SaleKind = .big
}

/// What a countdown shows: the sale running now, or the next one.
enum SaleCountdown: Equatable {
    case running(StoreSale)
    case upcoming(StoreSale)

    var sale: StoreSale {
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

    /// A fest gives way to a big sale starting within this long.
    static let bigSaleLead: TimeInterval = 7 * 24 * 60 * 60

    /// The sales from the Worker, else the dates built into the app; nil only when neither can be read.
    static func load() async -> [StoreSale]? {
        if let fetched = await fetch() { return fetched }
        return decode(Data(bundledJSON.utf8))
    }

    /// The sales, or nil when the file couldn't be fetched or read. Public data; nothing about the user is sent.
    static func fetch() async -> [StoreSale]? {
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
        /// "big" or "fest"; missing, or a kind this app doesn't know, counts as big.
        let kind: String?
    }

    /// Reads the file; nil for an unknown schema. A sale with an unreadable date, or ending before it starts, is skipped.
    static func decode(_ data: Data) -> [StoreSale]? {
        guard let document = try? JSONDecoder().decode(Document.self, from: data), document.schemaVersion == 1 else { return nil }
        let dates = ISO8601DateFormatter()
        return document.sales.compactMap { raw in
            guard let start = dates.date(from: raw.start), let end = dates.date(from: raw.end), start < end,
                  !raw.name.trimmingCharacters(in: .whitespaces).isEmpty else { return nil }
            return StoreSale(id: raw.id, store: raw.store, name: raw.name, start: start, end: end,
                             kind: raw.kind == "fest" ? .fest : .big)
        }
    }

    /// In order: the running big sale that ends soonest; a big sale starting within `bigSaleLead`; the running fest
    /// that ends soonest; the next sale to start. Nil when there is none.
    static func countdown(_ sales: [StoreSale], at now: Date) -> SaleCountdown? {
        func running(_ kind: SaleKind) -> StoreSale? {
            sales.filter { $0.kind == kind && $0.start <= now && now < $0.end }.min(by: { $0.end < $1.end })
        }
        let upcoming = sales.filter { $0.start > now }
        if let big = running(.big) { return .running(big) }
        if let soon = upcoming.filter({ $0.kind == .big && $0.start.timeIntervalSince(now) <= bigSaleLead })
            .min(by: { $0.start < $1.start }) {
            return .upcoming(soon)
        }
        if let fest = running(.fest) { return .running(fest) }
        return upcoming.min(by: { $0.start < $1.start }).map { .upcoming($0) }
    }
}

extension SaleCalendar {
    /// The dates built into this app version, used when the Worker can't be reached. A copy of `store/site/sales.json`:
    /// `python3 store/site/validate_sales.py` refreshes it, and a test in :domain fails when the two differ.
    static let bundledJSON = #"""
{
  "schemaVersion": 1,
  "sales": [
    {
      "id": "epic-holiday-2026",
      "store": "Epic Games Store",
      "name": "Epic Holiday Sale",
      "kind": "big",
      "start": "2026-12-10T16:00:00Z",
      "end": "2027-01-07T16:00:00Z"
    },
    {
      "id": "steam-winter-2026",
      "store": "Steam",
      "name": "Steam Winter Sale",
      "kind": "big",
      "start": "2026-12-17T18:00:00Z",
      "end": "2027-01-04T18:00:00Z"
    },
    {
      "id": "steam-desktop-companion-fest-2027",
      "store": "Steam",
      "name": "Desktop Companion Fest",
      "kind": "fest",
      "start": "2027-01-14T18:00:00Z",
      "end": "2027-01-18T18:00:00Z"
    },
    {
      "id": "steam-shop-keeper-fest-2027",
      "store": "Steam",
      "name": "Shop Keeper Fest",
      "kind": "fest",
      "start": "2027-01-25T18:00:00Z",
      "end": "2027-02-01T18:00:00Z"
    },
    {
      "id": "steam-sheep-fest-2027",
      "store": "Steam",
      "name": "Sheep Fest",
      "kind": "fest",
      "start": "2027-02-04T18:00:00Z",
      "end": "2027-02-08T18:00:00Z"
    },
    {
      "id": "steam-couch-co-op-fest-2027",
      "store": "Steam",
      "name": "Couch Co-Op Fest",
      "kind": "fest",
      "start": "2027-02-08T18:00:00Z",
      "end": "2027-02-15T18:00:00Z"
    },
    {
      "id": "steam-rhythm-fest-2027",
      "store": "Steam",
      "name": "Rhythm Fest",
      "kind": "fest",
      "start": "2027-03-08T18:00:00Z",
      "end": "2027-03-15T17:00:00Z"
    },
    {
      "id": "steam-spring-2027",
      "store": "Steam",
      "name": "Steam Spring Sale",
      "kind": "big",
      "start": "2027-03-18T17:00:00Z",
      "end": "2027-03-25T17:00:00Z"
    },
    {
      "id": "steam-dinos-vs-robots-fest-2027",
      "store": "Steam",
      "name": "Dinos vs. Robots Fest",
      "kind": "fest",
      "start": "2027-03-29T17:00:00Z",
      "end": "2027-04-05T17:00:00Z"
    },
    {
      "id": "steam-racing-fest-2027",
      "store": "Steam",
      "name": "Racing Fest",
      "kind": "fest",
      "start": "2027-04-12T17:00:00Z",
      "end": "2027-04-19T17:00:00Z"
    },
    {
      "id": "steam-witch-fest-2027",
      "store": "Steam",
      "name": "Witch Fest",
      "kind": "fest",
      "start": "2027-04-22T17:00:00Z",
      "end": "2027-04-26T17:00:00Z"
    },
    {
      "id": "steam-fighting-fest-2027",
      "store": "Steam",
      "name": "Fighting Fest",
      "kind": "fest",
      "start": "2027-04-26T17:00:00Z",
      "end": "2027-05-03T17:00:00Z"
    },
    {
      "id": "steam-real-time-strategy-fest-2027",
      "store": "Steam",
      "name": "Real-Time Strategy Fest",
      "kind": "fest",
      "start": "2027-05-10T17:00:00Z",
      "end": "2027-05-17T17:00:00Z"
    },
    {
      "id": "steam-mountaineering-fest-2027",
      "store": "Steam",
      "name": "Mountaineering Fest",
      "kind": "fest",
      "start": "2027-05-31T17:00:00Z",
      "end": "2027-06-03T17:00:00Z"
    },
    {
      "id": "steam-summer-2027",
      "store": "Steam",
      "name": "Steam Summer Sale",
      "kind": "big",
      "start": "2027-06-24T17:00:00Z",
      "end": "2027-07-08T17:00:00Z"
    }
  ]
}
"""#
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
