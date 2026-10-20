package pm.bam.gamedeals.shortcuts

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import pm.bam.gamedeals.MainActivity

/** Intent extra naming the [AppShortcut] a launcher shortcut or a widget asks [MainActivity] to open. */
internal const val EXTRA_APP_SHORTCUT = "extra_app_shortcut"

/** Intent extra carrying the ITAD game id for [AppShortcut.Game]. */
internal const val EXTRA_GAME_ID = "extra_game_id"

/**
 * Where a launcher shortcut (`res/xml/shortcuts.xml`) or a home-screen widget asks the app to go. [key] is the value
 * of [EXTRA_APP_SHORTCUT]; the static ones must match the `<extra>` values in `shortcuts.xml`. The iOS equivalent is
 * the App Intents in `AppShortcuts.swift`.
 */
internal sealed class AppShortcut(val key: String) {
    data object Giveaways : AppShortcut("giveaways")
    data object Deals : AppShortcut("deals")
    data object Waitlist : AppShortcut("waitlist")

    /** A game's page — a row in the Waitlist deals widget. */
    data class Game(val gameId: String) : AppShortcut(GAME_KEY)

    /**
     * An explicit [MainActivity] intent that opens this shortcut. A [Game] intent gets a per-game data URI so each
     * widget row's PendingIntent stays distinct (PendingIntents that differ only in extras are merged).
     */
    fun intent(context: Context): Intent = Intent(context, MainActivity::class.java)
        .putExtra(EXTRA_APP_SHORTCUT, key)
        .apply {
            if (this@AppShortcut is Game) {
                putExtra(EXTRA_GAME_ID, gameId)
                data = Uri.parse("pm.bam.gamedeals://shortcut/game/${Uri.encode(gameId)}")
            }
        }

    companion object {
        const val GAME_KEY = "game"
    }
}

/** Parses [EXTRA_APP_SHORTCUT] back into an [AppShortcut]; `null` for a normal launch or an unknown value. */
internal fun Intent.toAppShortcut(): AppShortcut? = when (getStringExtra(EXTRA_APP_SHORTCUT)) {
    AppShortcut.Giveaways.key -> AppShortcut.Giveaways
    AppShortcut.Deals.key -> AppShortcut.Deals
    AppShortcut.Waitlist.key -> AppShortcut.Waitlist
    AppShortcut.GAME_KEY -> getStringExtra(EXTRA_GAME_ID)?.takeIf { it.isNotBlank() }?.let(AppShortcut::Game)
    else -> null
}

/**
 * Hands shortcut requests from [MainActivity] to the nav graph. Same shape as `NotificationRouteBus`: buffered, so a
 * cold-start request waits for the nav graph to subscribe, and consumed exactly once.
 */
internal object AppShortcutBus {
    private val channel = Channel<AppShortcut>(Channel.BUFFERED)
    val requests: Flow<AppShortcut> = channel.receiveAsFlow()

    fun deliver(shortcut: AppShortcut) {
        channel.trySend(shortcut)
    }
}
