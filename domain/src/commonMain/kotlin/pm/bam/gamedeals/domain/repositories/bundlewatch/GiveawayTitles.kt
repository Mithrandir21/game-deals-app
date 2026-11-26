package pm.bam.gamedeals.domain.repositories.bundlewatch

/**
 * GamerPower giveaways carry only a title, written as "<game> (<store>) Giveaway" ("Hades (Epic Games) Giveaway"),
 * and no game id. Bundle watch matches them against waitlist titles exactly, after these steps, and nothing looser:
 * a looser match would tell someone waiting for "Hades II" that it is free when "Hades" is.
 */
internal data class GiveawayTitle(
    /** The game's title in [matchKey] form. */
    val key: String,
    /** The store in brackets, e.g. "Epic Games", when the title has one. */
    val store: String?,
)

private val GIVEAWAY_SUFFIX = Regex("""\s+giveaway\s*$""", RegexOption.IGNORE_CASE)
private val TRAILING_BRACKETS = Regex("""\s*\(([^()]*)\)\s*$""")

/** Reads a giveaway title into the game's match key and its store. */
internal fun parseGiveawayTitle(title: String): GiveawayTitle {
    var rest = title.trim().replace(GIVEAWAY_SUFFIX, "")
    val store = TRAILING_BRACKETS.find(rest)?.let { match ->
        rest = rest.substring(0, match.range.first)
        match.groupValues[1].trim().ifEmpty { null }
    }
    return GiveawayTitle(key = matchKey(rest), store = store)
}

/** Case, punctuation and spacing don't count: "Half-Life 2: Episode One" and "half life 2 episode one" match. */
internal fun matchKey(title: String): String = title.lowercase().filter { it.isLetterOrDigit() }
