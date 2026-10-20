import ImageIO
import SwiftUI
import UIKit
import WidgetKit

// Home Screen widget: PC games that are free to keep right now. It fetches GamerPower's public API itself rather
// than reading the app's data, so it needs no App Group or shared storage and works before the app is ever opened.
// Tapping it opens the app's Giveaways tab through the `pm.bam.gamedeals://giveaways` link (iOSApp.swift).

@main
struct GameDealsWidgetBundle: WidgetBundle {
    var body: some Widget {
        FreeGamesWidget()
    }
}

struct FreeGamesWidget: Widget {
    let kind = "FreeGamesWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: FreeGamesProvider()) { entry in
            FreeGamesWidgetView(entry: entry)
                .widgetURL(URL(string: "pm.bam.gamedeals://giveaways"))
                .widgetBackground()
        }
        .configurationDisplayName("Free Games")
        .description("PC games that are free to keep right now.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}

// MARK: - Data

struct FreeGame: Identifiable {
    let id: Int
    let title: String
    /// The usual price, e.g. "$19.99". Nil when GamerPower reports none.
    let worth: String?
    let ends: Date?
    let thumbnail: UIImage?
}

struct FreeGamesEntry: TimelineEntry {
    let date: Date
    /// Nil when the fetch failed; empty when it worked but nothing is free.
    let games: [FreeGame]?
}

struct FreeGamesProvider: TimelineProvider {
    func placeholder(in context: Context) -> FreeGamesEntry {
        FreeGamesEntry(date: Date(), games: FreeGamesProvider.sample)
    }

    func getSnapshot(in context: Context, completion: @escaping (FreeGamesEntry) -> Void) {
        if context.isPreview {
            completion(placeholder(in: context))
            return
        }
        Task { completion(FreeGamesEntry(date: Date(), games: await GamerPower.freeGames(limit: context.family.rows))) }
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<FreeGamesEntry>) -> Void) {
        Task {
            let games = await GamerPower.freeGames(limit: context.family.rows)
            // Giveaways change a few times a day. Retry sooner after a failure.
            let refresh = Date().addingTimeInterval(games == nil ? 30 * 60 : 3 * 60 * 60)
            completion(Timeline(entries: [FreeGamesEntry(date: Date(), games: games)], policy: .after(refresh)))
        }
    }

    static let sample = [
        FreeGame(id: 1, title: "A Free Adventure", worth: "$19.99", ends: Date().addingTimeInterval(3 * 86_400), thumbnail: nil),
        FreeGame(id: 2, title: "Another Great Game", worth: "$9.99", ends: nil, thumbnail: nil),
        FreeGame(id: 3, title: "Third Free Title", worth: nil, ends: nil, thumbnail: nil),
    ]
}

private extension WidgetFamily {
    var rows: Int {
        switch self {
        case .systemSmall: return 1
        case .systemMedium: return 3
        default: return 6
        }
    }
}

enum GamerPower {
    private struct Giveaway: Decodable {
        let id: Int
        let title: String
        let worth: String?
        let thumbnail: String?
        let end_date: String?
    }

    /// Active PC game giveaways, most popular first. Returns nil on any network or decoding failure.
    static func freeGames(limit: Int) async -> [FreeGame]? {
        guard let url = URL(string: "https://www.gamerpower.com/api/giveaways?platform=pc&type=game&sort-by=popularity") else { return nil }
        do {
            let (data, response) = try await URLSession.shared.data(from: url)
            // GamerPower answers 201 with an empty body when nothing matches.
            guard let http = response as? HTTPURLResponse else { return nil }
            if http.statusCode == 201 { return [] }
            guard http.statusCode == 200 else { return nil }
            let giveaways = try JSONDecoder().decode([Giveaway].self, from: data).prefix(limit)
            var games: [FreeGame] = []
            for giveaway in giveaways {
                games.append(FreeGame(
                    id: giveaway.id,
                    title: giveaway.title.strippingGiveawaySuffix,
                    worth: giveaway.worth.flatMap { $0 == "N/A" ? nil : $0 },
                    ends: giveaway.end_date.flatMap(endDateFormatter.date(from:)),
                    thumbnail: await thumbnail(giveaway.thumbnail)
                ))
            }
            return games
        } catch {
            return nil
        }
    }

    /// GamerPower's end dates are UTC "yyyy-MM-dd HH:mm:ss", or "N/A" (which fails to parse, meaning no end date).
    private static let endDateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "UTC")
        formatter.dateFormat = "yyyy-MM-dd HH:mm:ss"
        return formatter
    }()

    /// Downloads and shrinks a thumbnail. Widgets have a tight memory budget, so full-size images are never decoded.
    private static func thumbnail(_ urlString: String?) async -> UIImage? {
        guard let urlString, let url = URL(string: urlString),
              let download = try? await URLSession.shared.data(from: url),
              let source = CGImageSourceCreateWithData(download.0 as CFData, nil) else { return nil }
        let options: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceThumbnailMaxPixelSize: 240,
            kCGImageSourceCreateThumbnailWithTransform: true,
        ]
        guard let image = CGImageSourceCreateThumbnailAtIndex(source, 0, options as CFDictionary) else { return nil }
        return UIImage(cgImage: image)
    }
}

private extension String {
    /// "Hades (Epic Games) Giveaway" → "Hades (Epic Games)". GamerPower appends "Giveaway" to most titles.
    var strippingGiveawaySuffix: String {
        hasSuffix(" Giveaway") ? String(dropLast(" Giveaway".count)) : self
    }
}

// MARK: - Views

struct FreeGamesWidgetView: View {
    let entry: FreeGamesEntry
    @Environment(\.widgetFamily) private var family

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Label("Free to keep", systemImage: "gift.fill")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.tint)
            content
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    @ViewBuilder private var content: some View {
        switch entry.games {
        case nil:
            Message(text: "Couldn't load giveaways. Tap to open the app.")
        case let games? where games.isEmpty:
            Message(text: "No free PC games right now. Check back soon.")
        case let games?:
            if family == .systemSmall, let game = games.first {
                SmallGame(game: game)
            } else {
                ForEach(games) { GameRow(game: $0) }
            }
        }
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

private struct SmallGame: View {
    let game: FreeGame

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Thumbnail(image: game.thumbnail)
                .frame(maxWidth: .infinity)
                .frame(height: 56)
            Text(game.title)
                .font(.footnote.weight(.semibold))
                .lineLimit(2)
            Details(game: game)
        }
    }
}

private struct GameRow: View {
    let game: FreeGame

    var body: some View {
        HStack(spacing: 8) {
            Thumbnail(image: game.thumbnail)
                .frame(width: 64, height: 32)
            VStack(alignment: .leading, spacing: 1) {
                Text(game.title)
                    .font(.footnote.weight(.semibold))
                    .lineLimit(1)
                Details(game: game)
            }
        }
    }
}

private struct Details: View {
    let game: FreeGame

    var body: some View {
        HStack(spacing: 4) {
            if let worth = game.worth {
                Text(worth).strikethrough()
            }
            if let ends = game.ends {
                Text("ends \(ends, style: .relative)")
            }
        }
        .font(.caption2)
        .foregroundStyle(.secondary)
        .lineLimit(1)
    }
}

private struct Thumbnail: View {
    let image: UIImage?

    var body: some View {
        // The image fills an overlay so it is cropped to the caller's frame instead of overflowing it.
        Rectangle()
            .fill(.quaternary)
            .overlay {
                if let image {
                    Image(uiImage: image).resizable().aspectRatio(contentMode: .fill)
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: 4))
    }
}

private extension View {
    /// iOS 17 requires a container background for widgets; earlier versions draw the system background themselves.
    @ViewBuilder func widgetBackground() -> some View {
        if #available(iOS 17.0, *) {
            containerBackground(.background, for: .widget)
        } else {
            padding()
        }
    }
}
