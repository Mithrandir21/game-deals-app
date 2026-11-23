package pm.bam.gamedeals.iosApp

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import pm.bam.gamedeals.common.links.ShareLinks
import pm.bam.gamedeals.common.links.SharedLink

/**
 * Where a Siri / Shortcuts / Spotlight action (the Swift `AppIntent`s in `AppShortcuts.swift`) or a widget link
 * (`pm.bam.gamedeals://…`, handled in `iOSApp.swift`) asks the app to go.
 * iOS-only: Android has no equivalent entry point yet.
 */
internal sealed interface AppShortcut {
    data object Giveaways : AppShortcut
    data object Waitlist : AppShortcut
    data class SearchDeals(val query: String) : AppShortcut

    /** A game's page — a row in the Waitlist deals widget. */
    data class Game(val gameId: String) : AppShortcut

    /** A friend's wishlist — a shared `gamedeals.bam.pm/w/…` link. */
    data class SharedWishlist(val gameIds: List<String>) : AppShortcut
}

/**
 * Hands shortcut requests from the Swift intents to the nav host. Same shape as `NotificationRouteBus`: buffered, so
 * a request made while the app is still launching waits for the nav host to subscribe, and consumed exactly once.
 */
internal object AppShortcutBus {
    private val channel = Channel<AppShortcut>(Channel.BUFFERED)
    val requests: Flow<AppShortcut> = channel.receiveAsFlow()

    fun deliver(shortcut: AppShortcut) {
        channel.trySend(shortcut)
    }
}

/** Swift-facing: the "Show free games" intent. */
fun openGiveawaysShortcut() = AppShortcutBus.deliver(AppShortcut.Giveaways)

/** Swift-facing: the "Show my waitlist" intent. Signed out, the nav host offers sign-in over the empty list. */
fun openWaitlistShortcut() = AppShortcutBus.deliver(AppShortcut.Waitlist)

/** Swift-facing: the "Search deals" intent. A blank query just opens the Deals tab. */
fun searchDealsShortcut(query: String) = AppShortcutBus.deliver(AppShortcut.SearchDeals(query.trim()))

/** Swift-facing: a Waitlist deals widget row (`pm.bam.gamedeals://game/<id>`). A blank id is ignored. */
fun openGameShortcut(gameId: String) {
    if (gameId.isNotBlank()) AppShortcutBus.deliver(AppShortcut.Game(gameId))
}

/**
 * Swift-facing: a Universal Link (`https://gamedeals.bam.pm/g/<id>` or `…/w/<ids>`, see [ShareLinks]) opened from
 * another app.
 * Returns `false` for anything that isn't one of our links, so Swift can leave it alone.
 */
fun openSharedLink(url: String): Boolean = when (val link = ShareLinks.parse(url)) {
    is SharedLink.Game -> {
        AppShortcutBus.deliver(AppShortcut.Game(link.gameId))
        true
    }
    is SharedLink.Wishlist -> {
        AppShortcutBus.deliver(AppShortcut.SharedWishlist(link.gameIds))
        true
    }
    null -> false
}
