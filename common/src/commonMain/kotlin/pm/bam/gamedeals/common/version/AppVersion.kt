package pm.bam.gamedeals.common.version

/**
 * Semantic-version precedence, used by the remote minimum-version gate to decide whether the running build
 * is below the floor a maintainer has published.
 *
 * Deliberately **tolerant on input and strict on failure**: the right-hand side of every comparison is a
 * string typed into a remote-config UI, and the left-hand side is a `versionName` that a release script
 * could in principle mangle. Rather than guess at a malformed value, [compareVersions] returns `null` so
 * callers fail *open* (no gate) — a remote-config typo must never be able to lock users out of the app.
 *
 * Tolerances, all of which show up in real version strings:
 * - a leading `v` (`"v1.2.3"`) is stripped — Git tags carry it and it gets pasted around;
 * - build metadata (`"1.2.3+456"`) is ignored, per semver: it carries no precedence;
 * - missing components are zero-padded, so `"1.2"` and `"1.2.0"` compare equal.
 *
 * Pre-release ordering follows semver 2.0.0 §11: `1.2.0-beta` sits *below* `1.2.0`, numeric identifiers
 * compare numerically, alphanumeric ones compare by ASCII, numeric sorts below alphanumeric, and when one
 * identifier list is a prefix of the other the longer list wins.
 *
 * @return a negative number if [left] precedes [right], zero if they have equal precedence, a positive
 *   number if [left] follows [right], or `null` if either side could not be parsed.
 */
fun compareVersions(left: String, right: String): Int? {
    val a = parseVersion(left) ?: return null
    val b = parseVersion(right) ?: return null

    for (i in 0 until maxOf(a.numbers.size, b.numbers.size)) {
        // Zero-pad the shorter side so "1.2" == "1.2.0" rather than "1.2" < "1.2.0".
        val cmp = (a.numbers.getOrElse(i) { 0L }).compareTo(b.numbers.getOrElse(i) { 0L })
        if (cmp != 0) return cmp
    }

    return comparePreRelease(a.preRelease, b.preRelease)
}

/** Convenience for the gate's actual question: is [current] strictly older than [minimum]? Null-safe → false. */
fun isBelowMinimum(current: String, minimum: String): Boolean {
    val cmp = compareVersions(current, minimum) ?: return false // unparseable → fail open, no gate
    return cmp < 0
}

private class ParsedVersion(val numbers: List<Long>, val preRelease: List<String>)

private fun parseVersion(raw: String): ParsedVersion? {
    val trimmed = raw.trim().removePrefix("v").removePrefix("V")
    if (trimmed.isEmpty()) return null

    // Build metadata carries no precedence (semver §10), so drop it before anything else.
    val withoutBuild = trimmed.substringBefore('+')
    val core = withoutBuild.substringBefore('-')
    // Distinguish "no pre-release at all" from "a '-' followed by nothing": the latter is malformed, and
    // silently treating "1.2.3-" as "1.2.3" would let a typo'd minimum version gate users it shouldn't.
    val hasPreRelease = withoutBuild.contains('-')

    if (core.isEmpty()) return null
    val numbers = core.split('.').map { part ->
        // toLongOrNull rejects "", "1a", "-1" and anything that would silently coerce to a wrong number.
        part.toLongOrNull()?.takeIf { it >= 0 } ?: return null
    }

    val preReleaseParts = if (hasPreRelease) withoutBuild.substringAfter('-').split('.') else emptyList()
    // A trailing or doubled '-' produces an empty identifier, which is not a version we should guess at.
    if (preReleaseParts.any { it.isEmpty() }) return null

    return ParsedVersion(numbers, preReleaseParts)
}

private fun comparePreRelease(a: List<String>, b: List<String>): Int {
    // "1.0.0-beta" < "1.0.0". An absent pre-release outranks a present one (semver §11.3).
    if (a.isEmpty() && b.isEmpty()) return 0
    if (a.isEmpty()) return 1
    if (b.isEmpty()) return -1

    for (i in 0 until minOf(a.size, b.size)) {
        val cmp = comparePreReleaseIdentifier(a[i], b[i])
        if (cmp != 0) return cmp
    }
    // All shared identifiers equal: the longer list has higher precedence ("1.0.0-a" < "1.0.0-a.1").
    return a.size.compareTo(b.size)
}

private fun comparePreReleaseIdentifier(a: String, b: String): Int {
    val aNum = a.toLongOrNull()
    val bNum = b.toLongOrNull()
    return when {
        aNum != null && bNum != null -> aNum.compareTo(bNum)
        // Numeric identifiers always have lower precedence than alphanumeric ones.
        aNum != null -> -1
        bNum != null -> 1
        else -> a.compareTo(b)
    }
}
