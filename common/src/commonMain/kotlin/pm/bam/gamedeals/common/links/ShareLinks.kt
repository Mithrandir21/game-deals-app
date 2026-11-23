package pm.bam.gamedeals.common.links

/**
 * The public links the app shares, served by the `gamedeals-site` Worker on [HOST] (`store/site/`).
 *
 * A game link is `https://gamedeals.bam.pm/g/<ITAD game id>`. With the app installed, Android App Links and iOS
 * Universal Links open it on the game page; without it, the Worker serves a preview page with store and
 * get-the-app buttons. The link carries only the public game id, never anything about the person sharing it.
 *
 * A wishlist link is `https://gamedeals.bam.pm/w/<ids>`: up to [MAX_WISHLIST_GAMES] game ids, in the order picked,
 * each written as the id's 16 bytes in URL-safe base64 (22 characters, no padding) and run together. The games
 * live only in the link; nothing is stored anywhere. Ten games make a link of about 250 characters.
 *
 * The same path rules live in the Worker (`store/site/share-routes.mjs`); change both together.
 */
object ShareLinks {
    const val HOST = "gamedeals.bam.pm"
    const val GAME_PATH = "/g/"
    const val WISHLIST_PATH = "/w/"

    /** The most games one wishlist link carries; keeps the link short enough for any chat app. */
    const val MAX_WISHLIST_GAMES = 10

    /** "Get the app": the Worker sends Apple devices to the App Store and everyone else to Google Play. */
    const val APP_URL = "https://$HOST/app"

    /** The shareable link for a game, or `null` when [gameId] isn't an ITAD game id (never share a broken link). */
    fun game(gameId: String): String? = normalizeGameId(gameId)?.let { "https://$HOST$GAME_PATH$it" }

    /**
     * The shareable link for a wishlist of [gameIds], in their order with repeats dropped, or `null` when the list is
     * empty, holds more than [MAX_WISHLIST_GAMES] games, or has an id that isn't an ITAD game id.
     */
    fun wishlist(gameIds: List<String>): String? {
        val ids = gameIds.map { normalizeGameId(it) ?: return null }.distinct()
        if (ids.isEmpty() || ids.size > MAX_WISHLIST_GAMES) return null
        return "https://$HOST$WISHLIST_PATH" + ids.joinToString("") { encodeId(it) }
    }

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
        return when {
            path.startsWith(GAME_PATH) -> normalizeGameId(path.removePrefix(GAME_PATH))?.let(SharedLink::Game)
            path.startsWith(WISHLIST_PATH) -> decodeWishlist(path.removePrefix(WISHLIST_PATH))?.let(SharedLink::Wishlist)
            else -> null
        }
    }

    /**
     * The game ids in a wishlist link's last path segment, repeats dropped, or `null` unless it is 1 to
     * [MAX_WISHLIST_GAMES] whole ids. Base64 is case-sensitive, so the segment is never lower-cased.
     */
    private fun decodeWishlist(segment: String): List<String>? {
        if (segment.isEmpty() || segment.length % ENCODED_ID_LENGTH != 0) return null
        if (segment.length / ENCODED_ID_LENGTH > MAX_WISHLIST_GAMES) return null
        return segment.chunked(ENCODED_ID_LENGTH).map { decodeId(it) ?: return null }.distinct()
    }

    /** A lower-case UUID's 16 bytes as 22 URL-safe base64 characters. */
    private fun encodeId(uuid: String): String {
        val hex = uuid.replace("-", "")
        val out = StringBuilder(ENCODED_ID_LENGTH)
        var buffer = 0
        var bits = 0
        for (char in hex) {
            buffer = (buffer shl 4) or char.digitToInt(16)
            bits += 4
            if (bits >= 6) {
                bits -= 6
                out.append(BASE64URL[(buffer shr bits) and 0x3F])
                buffer = buffer and ((1 shl bits) - 1)
            }
        }
        // 128 bits leave 2 over: pad them with zeros into a last character.
        if (bits > 0) out.append(BASE64URL[(buffer shl (6 - bits)) and 0x3F])
        return out.toString()
    }

    /**
     * 22 base64 characters back to a lower-case UUID, or `null` for a character outside the URL-safe alphabet or a
     * last character with stray low bits (so every id has exactly one spelling).
     */
    private fun decodeId(token: String): String? {
        val hex = StringBuilder(32)
        var buffer = 0
        var bits = 0
        for (char in token) {
            val value = BASE64URL.indexOf(char)
            if (value < 0) return null
            buffer = (buffer shl 6) or value
            bits += 6
            while (bits >= 4 && hex.length < 32) {
                bits -= 4
                hex.append(HEX[(buffer shr bits) and 0xF])
            }
            buffer = buffer and ((1 shl bits) - 1)
        }
        if (buffer != 0) return null
        return "${hex.substring(0, 8)}-${hex.substring(8, 12)}-${hex.substring(12, 16)}-${hex.substring(16, 20)}-${hex.substring(20)}"
    }

    /** ITAD game ids are UUIDs; lower-case them so one game always has one link. */
    private fun normalizeGameId(raw: String): String? = raw.lowercase().takeIf { ITAD_ID.matches(it) }

    private fun String.removePrefixIgnoreCase(prefix: String): String? =
        if (startsWith(prefix, ignoreCase = true)) substring(prefix.length) else null

    private const val ENCODED_ID_LENGTH = 22
    private const val BASE64URL = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    private const val HEX = "0123456789abcdef"

    private val ITAD_ID = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
}

/** Where a shared link points. */
sealed interface SharedLink {
    data class Game(val gameId: String) : SharedLink

    /** A friend's wishlist: 1 to [ShareLinks.MAX_WISHLIST_GAMES] game ids, in the order they picked them. */
    data class Wishlist(val gameIds: List<String>) : SharedLink
}
