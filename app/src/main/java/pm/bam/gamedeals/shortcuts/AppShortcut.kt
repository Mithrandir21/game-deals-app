package pm.bam.gamedeals.shortcuts

import android.content.Intent
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** Intent extra naming the [AppShortcut] a launcher shortcut or the Free Games widget asks [pm.bam.gamedeals.MainActivity] to open. */
internal const val EXTRA_APP_SHORTCUT = "extra_app_shortcut"

/**
 * Where a launcher shortcut (`res/xml/shortcuts.xml`) or the Free Games widget asks the app to go. [key] is the
 * value of [EXTRA_APP_SHORTCUT] and must match the `<extra>` values in `shortcuts.xml`. The iOS equivalent is the
 * App Intents in `AppShortcuts.swift`.
 */
internal enum class AppShortcut(val key: String) {
    GIVEAWAYS("giveaways"),
    DEALS("deals"),
    WAITLIST("waitlist"),
}

/** Parses [EXTRA_APP_SHORTCUT] back into an [AppShortcut]; `null` for a normal launch or an unknown value. */
internal fun Intent.toAppShortcut(): AppShortcut? =
    getStringExtra(EXTRA_APP_SHORTCUT)?.let { key -> AppShortcut.entries.firstOrNull { it.key == key } }

/**
 * Hands shortcut requests from [pm.bam.gamedeals.MainActivity] to the nav graph. Same shape as `NotificationRouteBus`:
 * buffered, so a cold-start request waits for the nav graph to subscribe, and consumed exactly once.
 */
internal object AppShortcutBus {
    private val channel = Channel<AppShortcut>(Channel.BUFFERED)
    val requests: Flow<AppShortcut> = channel.receiveAsFlow()

    fun deliver(shortcut: AppShortcut) {
        channel.trySend(shortcut)
    }
}
