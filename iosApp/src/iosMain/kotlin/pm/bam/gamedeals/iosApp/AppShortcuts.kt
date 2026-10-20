package pm.bam.gamedeals.iosApp

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * Where a Siri / Shortcuts / Spotlight action (the Swift `AppIntent`s in `AppShortcuts.swift`) asks the app to go.
 * iOS-only: Android has no equivalent entry point yet.
 */
internal sealed interface AppShortcut {
    data object Giveaways : AppShortcut
    data object Waitlist : AppShortcut
    data class SearchDeals(val query: String) : AppShortcut
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
