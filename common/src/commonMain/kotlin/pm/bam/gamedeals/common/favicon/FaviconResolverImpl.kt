package pm.bam.gamedeals.common.favicon

class FaviconResolverImpl : FaviconResolver {

    override fun resolve(siteUrl: String): FaviconRef {
        val host = hostOf(siteUrl)
        val brandKey = host?.let { brandKeyOf(it) }
        val url = brandFaviconUrls[brandKey] ?: host?.let { "https://$it/favicon.ico" }
        return FaviconRef(url = url, cacheKey = brandKey)
    }

    private fun hostOf(url: String): String? = url
        .substringAfter("://", missingDelimiterValue = "")
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
        .takeIf { it.isNotBlank() }

    // Brand keys dedup the Coil cache across subdomain variants of the same brand. IGDB stores
    // publisher-supplied URLs verbatim, so different games hit different hosts (epicgames.com,
    // store.epicgames.com, www.epicgames.com, …) — without a brand-shared key each variant is
    // fetched and cached independently, producing the "Epic icon shows for some games but not
    // others" UX visible before this normalisation.
    private fun brandKeyOf(host: String): String? {
        val h = host.lowercase()
        return when {
            "epicgames.com" in h -> "brand:epicgames"
            "steampowered.com" in h || "steamcommunity.com" in h -> "brand:steam"
            "xbox.com" in h -> "brand:xbox"
            "playstation.com" in h -> "brand:playstation"
            "nintendo.com" in h -> "brand:nintendo"
            "gog.com" in h -> "brand:gog"
            "youtube.com" in h -> "brand:youtube"
            "twitch.tv" in h -> "brand:twitch"
            "x.com" in h || "twitter.com" in h -> "brand:x"
            "facebook.com" in h -> "brand:facebook"
            "instagram.com" in h -> "brand:instagram"
            "reddit.com" in h -> "brand:reddit"
            "wikipedia.org" in h -> "brand:wikipedia"
            "itch.io" in h -> "brand:itch"
            else -> null
        }
    }

    // Canonical favicon URLs for brands whose IGDB-stored URLs vary across games. Empirically
    // discovered via the FaviconProbe diagnostic — e.g. `epicgames.com/favicon.ico` returns a
    // real 48x48 image while `store.epicgames.com/favicon.ico` returns bytes that won't decode.
    // Brands whose every variant fails (Discord, Bluesky) are intentionally omitted — those
    // chips render label-only rather than chase versioned CDN paths we'd have to babysit.
    private companion object {
        private val brandFaviconUrls: Map<String, String> = mapOf(
            "brand:epicgames" to "https://epicgames.com/favicon.ico",
            "brand:steam" to "https://store.steampowered.com/favicon.ico",
            "brand:xbox" to "https://www.xbox.com/favicon.ico",
            "brand:playstation" to "https://store.playstation.com/favicon.ico",
            "brand:nintendo" to "https://www.nintendo.com/favicon.ico",
            "brand:gog" to "https://www.gog.com/favicon.ico",
            "brand:youtube" to "https://www.youtube.com/favicon.ico",
            "brand:twitch" to "https://www.twitch.tv/favicon.ico",
            "brand:x" to "https://x.com/favicon.ico",
            "brand:facebook" to "https://www.facebook.com/favicon.ico",
            "brand:instagram" to "https://www.instagram.com/favicon.ico",
            "brand:reddit" to "https://www.reddit.com/favicon.ico",
            "brand:wikipedia" to "https://en.wikipedia.org/favicon.ico",
            "brand:itch" to "https://itch.io/favicon.ico",
        )
    }
}
