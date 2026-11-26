package pm.bam.gamedeals.remote.igdb

/**
 * Strip store-decorated suffixes from a deal title before handing it to IGDB.
 *
 * CheapShark store entries frequently append edition info IGDB's catalogue doesn't carry verbatim
 * (e.g. "Suicide Squad: Kill the Justice League - Digital Deluxe Edition" → IGDB's "Suicide Squad:
 * Kill the Justice League"). The trimmed form gives the IGDB lookup a fighting chance.
 *
 * Removes, iteratively until stable:
 *  1. A trailing parenthetical group ("(Steam)", "(Digital)", "(2016)").
 *  2. The space-delimited "dash + edition-tail" suffix at the end (where the tail ENDS WITH one
 *     of the edition keywords). Iterating handles chained suffixes like "X - GOTY - Remaster".
 *
 * Design choices:
 *  - Delimiter is `\s+[-–—]\s+` (whitespace on BOTH sides of the dash). This preserves hyphenated
 *    game names — "Half-Life 2", "Spider-Man", "X-COM" all keep their internal hyphens because
 *    those have no surrounding whitespace.
 *  - Inner hyphens in the tail are allowed — "GOTY-Edition" or "Special-Edition" still strip in
 *    one shot, because the boundary is space-padded, not "any hyphen".
 *  - Keyword check is `endsWith` on the lowercased tail (not `contains`). This keeps subtitles
 *    that happen to contain "edition" / "cut" as a non-final word intact —
 *    "Game - The Edition Strikes Back" is preserved verbatim.
 *  - Colon is NOT a delimiter; titles like "Skyrim: Special Edition" or "Pokemon Yellow Version:
 *    Special Pikachu Edition" stay intact because the suffix is part of the IGDB-canonical name.
 */
internal fun normalizeTitleForLookup(title: String): String {
    var result = title.trim()
    while (true) {
        var changed = false
        val withoutParen = result.replace(PARENTHETICAL_TAIL, "").trimEnd()
        if (withoutParen != result) {
            result = withoutParen
            changed = true
        }
        val lastDelim = EDITION_DELIMITER.findAll(result).lastOrNull()
        if (lastDelim != null) {
            val tail = result.substring(lastDelim.range.last + 1).trim().lowercase()
            if (EDITION_KEYWORDS.any { tail.endsWith(it) }) {
                result = result.substring(0, lastDelim.range.first).trimEnd()
                changed = true
            }
        }
        if (!changed) return result
    }
}

private val PARENTHETICAL_TAIL = Regex("""\s*\([^()]*\)\s*$""")
private val EDITION_DELIMITER = Regex("""\s+[\-–—]\s+""")
private val EDITION_KEYWORDS = listOf(
    "edition",
    "cut",
    "remaster",
    "remastered",
    "goty",
    "game of the year",
    "bundle",
)
