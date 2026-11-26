package pm.bam.gamedeals.common.favicon

/**
 * Maps a website URL to the favicon URL we should hand to Coil plus the cache key Coil should
 * store it under. Brand-keying ensures multiple subdomain variants of the same brand (e.g.
 * `epicgames.com`, `store.epicgames.com`, `www.epicgames.com`) share one Coil cache entry,
 * fetched from a single canonical URL we know works. Stateless; safe to bind as a Koin singleton.
 */
interface FaviconResolver {
    fun resolve(siteUrl: String): FaviconRef
}

/**
 * Result of resolving a website URL.
 *
 * - [url] is the favicon URL to pass to Coil's `AsyncImage`, or `null` when the input can't yield
 *   a host (e.g. malformed input).
 * - [cacheKey] is the Coil memory/disk cache key to override with. `null` means "use [url] as the
 *   cache key" (Coil's default).
 */
data class FaviconRef(
    val url: String?,
    val cacheKey: String?,
)
