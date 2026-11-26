import AppIntents
import ComposeApp

// Siri, Shortcuts and Spotlight actions. Each one opens the app and hands the request to the shared nav host
// through `AppShortcutBus` (AppShortcuts.kt), which navigates once the UI is up — also on a cold start.
// App Intents need iOS 16; on iOS 15 the app simply has no shortcuts.

@available(iOS 16.0, *)
struct ShowFreeGamesIntent: AppIntent {
    static var title: LocalizedStringResource = "Show Free Games"
    static var description = IntentDescription("Opens the games that are free to keep right now.")
    static var openAppWhenRun: Bool = true

    @MainActor
    func perform() async throws -> some IntentResult {
        AppShortcutsKt.openGiveawaysShortcut()
        return .result()
    }
}

@available(iOS 16.0, *)
struct ShowWaitlistIntent: AppIntent {
    static var title: LocalizedStringResource = "Show My Waitlist"
    static var description = IntentDescription("Opens the games on your IsThereAnyDeal waitlist.")
    static var openAppWhenRun: Bool = true

    @MainActor
    func perform() async throws -> some IntentResult {
        AppShortcutsKt.openWaitlistShortcut()
        return .result()
    }
}

@available(iOS 16.0, *)
struct SearchDealsIntent: AppIntent {
    static var title: LocalizedStringResource = "Search Game Deals"
    static var description = IntentDescription("Searches every store's current deals for a game.")
    static var openAppWhenRun: Bool = true

    @Parameter(title: "Game", requestValueDialog: "Which game?")
    var query: String

    static var parameterSummary: some ParameterSummary {
        Summary("Search deals for \(\.$query)")
    }

    @MainActor
    func perform() async throws -> some IntentResult {
        AppShortcutsKt.searchDealsShortcut(query: query)
        return .result()
    }
}

// Zero-setup shortcuts: they appear in Spotlight and the Shortcuts app, and Siri answers the phrases, without the
// user creating anything. A free-text parameter can't be part of a phrase, so Siri asks "Which game?" instead.
@available(iOS 16.0, *)
struct GameDealsShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: ShowFreeGamesIntent(),
            phrases: [
                "Show free games in \(.applicationName)",
                "What games are free in \(.applicationName)",
            ],
            shortTitle: "Free Games",
            systemImageName: "gift"
        )
        AppShortcut(
            intent: SearchDealsIntent(),
            phrases: [
                "Search deals in \(.applicationName)",
                "Find a game deal in \(.applicationName)",
            ],
            shortTitle: "Search Deals",
            systemImageName: "magnifyingglass"
        )
        AppShortcut(
            intent: ShowWaitlistIntent(),
            phrases: [
                "Show my waitlist in \(.applicationName)",
            ],
            shortTitle: "My Waitlist",
            systemImageName: "bookmark"
        )
    }
}
