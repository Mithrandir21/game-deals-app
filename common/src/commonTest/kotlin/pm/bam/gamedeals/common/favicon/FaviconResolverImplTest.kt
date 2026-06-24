package pm.bam.gamedeals.common.favicon

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FaviconResolverImplTest {

    private val resolver = FaviconResolverImpl()

    @Test
    fun steam_subdomain_variants_share_brand_key_and_canonical_url() {
        val store = resolver.resolve("https://store.steampowered.com/app/1240440")
        val community = resolver.resolve("https://steamcommunity.com/app/12345")

        assertEquals("https://store.steampowered.com/favicon.ico", store.url)
        assertEquals("brand:steam", store.cacheKey)
        assertEquals(store.url, community.url, "Both Steam subdomains must resolve to the same canonical favicon URL")
        assertEquals(store.cacheKey, community.cacheKey, "Both Steam subdomains must share a brand cache key")
    }

    @Test
    fun epic_subdomain_variants_route_to_bare_domain_canonical_url() {
        val storeSub = resolver.resolve("https://store.epicgames.com/en-US/p/luna-abyss-696590")
        val bare = resolver.resolve("https://epicgames.com/p/lego-batman-c82b6b")
        val wwwSub = resolver.resolve("https://www.epicgames.com/store/en-US/p/halo-infinite")

        // All three resolve to the bare-domain favicon URL (empirically the only Epic variant that
        // actually serves a decodable image — store.epicgames.com returns garbage bytes).
        listOf(storeSub, bare, wwwSub).forEachIndexed { i, ref ->
            assertEquals("https://epicgames.com/favicon.ico", ref.url, "Epic variant $i")
            assertEquals("brand:epicgames", ref.cacheKey, "Epic variant $i")
        }
    }

    @Test
    fun unknown_brand_falls_back_to_host_based_url_and_null_cache_key() {
        // Indie publisher site — not a known brand. Coil will fall back to using the URL itself
        // as the cache key.
        val ref = resolver.resolve("https://lunaabyss.com/")
        assertEquals("https://lunaabyss.com/favicon.ico", ref.url)
        assertNull(ref.cacheKey)
    }

    @Test
    fun malformed_url_yields_null_favicon_and_null_cache_key() {
        val ref = resolver.resolve("not-a-url")
        assertNull(ref.url)
        assertNull(ref.cacheKey)
    }

    @Test
    fun host_matching_is_case_insensitive() {
        val ref = resolver.resolve("HTTPS://STORE.STEAMPOWERED.COM/app/123")
        assertEquals("https://store.steampowered.com/favicon.ico", ref.url)
        assertEquals("brand:steam", ref.cacheKey)
    }

    @Test
    fun query_string_and_fragment_are_stripped_from_host() {
        val ref = resolver.resolve("https://x.com/foo?bar=baz#frag")
        assertEquals("https://x.com/favicon.ico", ref.url)
        assertEquals("brand:x", ref.cacheKey)
    }

    @Test
    fun twitter_and_x_share_brand_key_for_the_xitter_rename() {
        val twitter = resolver.resolve("https://twitter.com/SomeHandle")
        val x = resolver.resolve("https://x.com/SomeHandle")
        assertEquals(twitter.cacheKey, x.cacheKey)
        assertEquals("brand:x", twitter.cacheKey)
    }

    @Test
    fun bluesky_resolves_to_its_static_apple_touch_icon_not_root_favicon() {
        // bsky.app/favicon.ico 404s; the brand map points at the static PNG that actually decodes.
        val ref = resolver.resolve("https://bsky.app/profile/somegame.bsky.social")
        assertEquals("https://bsky.app/static/apple-touch-icon.png", ref.url)
        assertEquals("brand:bluesky", ref.cacheKey)
    }

    @Test
    fun host_only_url_with_no_path_still_resolves() {
        val ref = resolver.resolve("https://www.gog.com")
        assertEquals("https://www.gog.com/favicon.ico", ref.url)
        assertEquals("brand:gog", ref.cacheKey)
    }
}
