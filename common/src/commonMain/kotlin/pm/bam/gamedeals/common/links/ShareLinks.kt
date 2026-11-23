package pm.bam.gamedeals.common.links

/**
 * The public links the app shares, served by the `gamedeals-site` Worker on [HOST] (`store/site/`).
 *
 * A game link is `https://gamedeals.bam.pm/g/<ITAD game id>`. With the app installed, Android App Links and iOS
 * Universal Links open it on the game page; without it, the Worker serves a preview page with store and
 * get-the-app buttons. The link carries only the public game id, never anything about the person sharing it.
 *
 * The same path rules live in the Worker (`store/site/share-routes.mjs`); change both together.
 */
object ShareLinks {
    const val HOST = "gamedeals.bam.pm"
    const val GAME_PATH = "/g/"

    /** "Get the app": the Worker sends Apple devices to the App Store and everyone else to Google Play. */
    const val APP_URL = "https://$HOST/app"

    /** The shareable link for a game, or `null` when [gameId] isn't an ITAD game id (never share a broken link). */
    fun game(gameId: String): String? = normalizeGameId(gameId)?.let { "https://$HOST$GAME_PATH$it" }

    /**
     * Reads a link back into what it points at, or `null` for anything that isn't one of ours: another host, plain
     * http, an unknown path, or a malformed id. Query strings and fragments are ignored; a trailing slash is allowed.
     */
    fun parse(url: String): SharedLink? {
        val rest = url.trim().removePrefixIgnoreCase("https://") ?: return null
        val slash = rest.indexOf('/')
        if (slash < 0) return null
        if (!rest.substring(0, slash).equals(HOST, ignoreCase = true)) return null
        val path = rest.substring(slash).substringBefore('#').substringBefore('?').removeSuffix("/")
        if (!path.startsWith(GAME_PATH)) return null
        return normalizeGameId(path.removePrefix(GAME_PATH))?.let(SharedLink::Game)
    }

    /** ITAD game ids are UUIDs; lower-case them so one game always has one link. */
    private fun normalizeGameId(raw: String): String? = raw.lowercase().takeIf { ITAD_ID.matches(it) }

    private fun String.removePrefixIgnoreCase(prefix: String): String? =
        if (startsWith(prefix, ignoreCase = true)) substring(prefix.length) else null

    private val ITAD_ID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
}

/** Where a shared link points. */
sealed interface SharedLink {
    data class Game(val gameId: String) : SharedLink
}
